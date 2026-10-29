"""Source agreement can lock a release year without reconciliation.

The combined submission precheck runs before this stage. Wikipedia extraction
can corroborate a year when the three structured sources do not agree.
"""

import logging
from collections import Counter
from enum import StrEnum

from app.config import settings
from app.metadata.llm import extract_structured, synthesize_with_model
from app.metadata.prompts import build_four_sources_prompt, build_wikipedia_extraction_prompt
from app.metadata.schemas import LlmExtractionResult
from app.metadata.stages.errors import StageFailure
from app.metadata.stages.schemas import StageProvider

logger = logging.getLogger(__name__)


class VerificationRoute(StrEnum):
    LOCKED = "locked"
    LOCKED_WITH_WIKIPEDIA = "locked_with_wikipedia"
    LLM_RECONCILED = "llm_reconciled"
    MANUAL_REVIEW = "manual_review"


class VerificationStatus(StrEnum):
    VERIFIED = "VERIFIED"
    NEEDS_REVIEW = "NEEDS_REVIEW"
    MANUAL_ENTRY = "MANUAL_ENTRY"


def _extract_earliest_year(candidates: list[dict]) -> int | None:
    """Takes the earliest valid year across all candidates returned by a
    single source. MusicBrainz returns its track and parent-album candidates
    in one list; sources that answer with a single query return that query's
    candidates. This picks the minimum across whatever the source returned."""
    resolved_years = []
    for candidate in candidates:
        if "year" in candidate and candidate["year"] is not None:
            resolved_years.append(int(candidate["year"]))
        elif "date" in candidate and candidate["date"]:
            year_digits = str(candidate["date"]).lstrip("+-")[:4]
            if year_digits.isdigit():
                resolved_years.append(int(year_digits))
    return min(resolved_years) if resolved_years else None


def _all_three_agree(
    musicbrainz_year: int | None,
    discogs_year: int | None,
    wikidata_year: int | None,
) -> bool:
    """True only when all three sources returned a year and all three
    match exactly. A missing source (None) never counts as agreement."""
    return (
        musicbrainz_year is not None
        and discogs_year is not None
        and wikidata_year is not None
        and musicbrainz_year == discogs_year == wikidata_year
    )


MINIMUM_SUPPORTING_SOURCES = 2
TRACK_QUERY = "track"
ALBUM_QUERY = "album"


def _earliest_year_for_query(candidates: list[dict], query: str) -> int | None:
    return _extract_earliest_year([candidate for candidate in candidates if candidate.get("query") == query])


def _earliest_supported_year(
    musicbrainz_year: int | None,
    discogs_year: int | None,
    wikidata_candidates: list[dict],
    wikipedia_year: int | None,
) -> int | None:
    """The earliest year that at least two sources report. A song's year is its earliest
    release, and a later year from another source (a single after the album, a reissue)
    never contradicts an earlier release, so later years do not outvote it; an earlier year
    from a single source does not count, because that is where bootlegs, promos, and wrong
    dates show up.

    Wikidata's album date is the album's original edition, which is wrong for a song only
    added to a later edition, so it votes only when MusicBrainz, which checks the editions
    that actually contain the recording, reports the same year."""
    wikidata_track_year = _earliest_year_for_query(wikidata_candidates, TRACK_QUERY)
    wikidata_album_year = _earliest_year_for_query(wikidata_candidates, ALBUM_QUERY)
    album_vote = wikidata_album_year if wikidata_album_year == musicbrainz_year else None
    votes = Counter(year for year in (musicbrainz_year, discogs_year, wikidata_track_year, album_vote, wikipedia_year) if year)
    supported_years = [year for year, count in votes.items() if count >= MINIMUM_SUPPORTING_SOURCES]
    return min(supported_years) if supported_years else None


def evaluate_lock(
    title: str,
    artist: str,
    musicbrainz_candidates: list[dict],
    discogs_candidates: list[dict],
    wikidata_candidates: list[dict],
    wikipedia_entries: list[dict],
    *,
    raise_on_failure: bool = False,
) -> tuple[int | None, str, VerificationRoute]:
    """Runs the full lock-or-LLM decision for one song.

    Returns (release_year, confidence, route) where route is one of:
    - LOCKED: all three structured sources agreed, no LLM called
    - LOCKED_WITH_WIKIPEDIA: at least two sources support the earliest year, no reconciliation called
    - LLM_RECONCILED: sources disagreed, Wikipedia+reconciliation ran
    - MANUAL_REVIEW: no source, including Wikipedia, had any data
    """
    musicbrainz_year = _extract_earliest_year(musicbrainz_candidates)
    discogs_year = _extract_earliest_year(discogs_candidates)
    wikidata_year = _extract_earliest_year(wikidata_candidates)

    if _all_three_agree(musicbrainz_year, discogs_year, wikidata_year):
        return musicbrainz_year, "high", VerificationRoute.LOCKED

    extraction_result = _run_wikipedia_extraction(title, artist, wikipedia_entries, raise_on_failure=raise_on_failure)
    wikipedia_year = extraction_result.release_year if extraction_result else None
    wikipedia_confidence = extraction_result.confidence if extraction_result else None

    wikipedia_assisted_lock_year = _earliest_supported_year(
        musicbrainz_year, discogs_year, wikidata_candidates, wikipedia_year
    )
    if wikipedia_assisted_lock_year is not None:
        return wikipedia_assisted_lock_year, "high", VerificationRoute.LOCKED_WITH_WIKIPEDIA

    all_sources_empty = (
        musicbrainz_year is None
        and discogs_year is None
        and wikidata_year is None
        and wikipedia_year is None
    )
    if all_sources_empty:
        return None, "low", VerificationRoute.MANUAL_REVIEW

    reconciliation_result = _run_four_source_reconciliation(
        title,
        artist,
        musicbrainz_candidates,
        discogs_candidates,
        wikidata_candidates,
        wikipedia_year,
        wikipedia_confidence,
        raise_on_failure=raise_on_failure,
    )
    if reconciliation_result is None or reconciliation_result.release_year is None:
        return None, "low", VerificationRoute.MANUAL_REVIEW

    return reconciliation_result.release_year, reconciliation_result.confidence, VerificationRoute.LLM_RECONCILED


def route_to_verification_status(route: VerificationRoute) -> VerificationStatus:
    """Maps a pipeline route to the VerificationStatus the core service
    should persist on the Song row. Both lock routes map to VERIFIED (immutable);
    LLM_RECONCILED maps to NEEDS_REVIEW (one source or LLM answered but
    sources didn't unanimously agree); MANUAL_REVIEW maps to MANUAL_ENTRY
    (no source had data, a human must supply the year)."""
    if route in {VerificationRoute.LOCKED, VerificationRoute.LOCKED_WITH_WIKIPEDIA}:
        return VerificationStatus.VERIFIED
    if route == VerificationRoute.LLM_RECONCILED:
        return VerificationStatus.NEEDS_REVIEW
    return VerificationStatus.MANUAL_ENTRY


def _run_wikipedia_extraction(
    title: str,
    artist: str,
    wikipedia_entries: list[dict],
    *,
    raise_on_failure: bool = False,
) -> LlmExtractionResult | None:
    """Runs the dedicated reading-comprehension extraction pass over
    Wikipedia's article prose. Only called when the three structured
    sources didn't lock. Resolve tolerates failures; stage calls propagate them
    with the provider so core can retry."""
    try:
        extraction_prompt = build_wikipedia_extraction_prompt(title, artist, wikipedia_entries)
        return extract_structured(extraction_prompt, LlmExtractionResult)
    except Exception as extraction_error:
        if raise_on_failure:
            raise StageFailure(StageProvider.DEEPINFRA, extraction_error) from extraction_error
        logger.warning("Wikipedia extraction failed for %r by %r: %s", title, artist, extraction_error)
        return None


def _run_four_source_reconciliation(
    title: str,
    artist: str,
    musicbrainz_candidates: list[dict],
    discogs_candidates: list[dict],
    wikidata_candidates: list[dict],
    wikipedia_year: int | None,
    wikipedia_confidence: str | None,
    *,
    raise_on_failure: bool = False,
) -> LlmExtractionResult | None:
    """Runs the four-source reconciliation call that decides among
    conflicting or partial structured-source candidates. Only called when
    the three structured sources didn't lock. Resolve tolerates failures; stage
    calls propagate them with the provider so core can retry."""
    try:
        reconciliation_prompt = build_four_sources_prompt(
            title,
            artist,
            musicbrainz_candidates,
            discogs_candidates,
            wikidata_candidates,
            wikipedia_year,
            wikipedia_confidence,
        )
        return synthesize_with_model(reconciliation_prompt, settings.reconciliation_model, LlmExtractionResult)
    except Exception as reconciliation_error:
        if raise_on_failure:
            raise StageFailure(StageProvider.OPENAI, reconciliation_error) from reconciliation_error
        logger.warning(
            "Four-source reconciliation failed for %r by %r: %s", title, artist, reconciliation_error
        )
        return None
