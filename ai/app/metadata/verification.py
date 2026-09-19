"""Lock-evaluation logic for song release-year verification.

The decided pipeline shape: MusicBrainz, Discogs, and Wikidata are
queried first. If all three agree on the same year, that year is locked
with no LLM involvement. Only when they disagree (partial agreement,
a missing source, or three-way disagreement) does the pipeline fetch
and extract Wikipedia (DeepSeek-V4-Flash) and run four-source
reconciliation (gpt-5-nano). A genuine no-answer from all sources,
including Wikipedia, routes to manual review rather than guessing.

Validated against a 70-song test set: 99% accuracy overall, 53% of
songs locked with zero LLM calls. See docs/DECISIONS.md's
"Metadata pipeline final shape" entry and ai/spikes/run_conditional_pipeline.py.
"""

import logging
from enum import StrEnum

from app.config import settings
from app.metadata.llm import extract_structured, synthesize_with_model
from app.metadata.prompts import build_four_sources_prompt, build_wikipedia_extraction_prompt
from app.metadata.schemas import LlmExtractionResult

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
    single source, matching the spike's source_earliest_year logic. Each
    source already runs a track+album query and returns both in one list;
    this picks the minimum across the whole list."""
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


def _find_wikipedia_assisted_lock_year(candidate_years: list[int | None]) -> int | None:
    resolved_years = [candidate_year for candidate_year in candidate_years if candidate_year is not None]
    minimum_source_count = 3
    maximum_cluster_spread = 1

    if len(resolved_years) < minimum_source_count:
        return None

    for candidate_year in set(resolved_years):
        if resolved_years.count(candidate_year) >= minimum_source_count:
            return candidate_year

    if max(resolved_years) - min(resolved_years) <= maximum_cluster_spread:
        return min(resolved_years)

    return None


def evaluate_lock(
    title: str,
    artist: str,
    musicbrainz_candidates: list[dict],
    discogs_candidates: list[dict],
    wikidata_candidates: list[dict],
    wikipedia_entries: list[dict],
) -> tuple[int | None, str, VerificationRoute]:
    """Runs the full lock-or-LLM decision for one song.

    Returns (release_year, confidence, route) where route is one of:
    - LOCKED: all three structured sources agreed, no LLM called
    - LOCKED_WITH_WIKIPEDIA: Wikipedia corroborated three source years, no reconciliation called
    - LLM_RECONCILED: sources disagreed, Wikipedia+reconciliation ran
    - MANUAL_REVIEW: no source, including Wikipedia, had any data
    """
    musicbrainz_year = _extract_earliest_year(musicbrainz_candidates)
    discogs_year = _extract_earliest_year(discogs_candidates)
    wikidata_year = _extract_earliest_year(wikidata_candidates)

    if _all_three_agree(musicbrainz_year, discogs_year, wikidata_year):
        return musicbrainz_year, "high", VerificationRoute.LOCKED

    extraction_result = _run_wikipedia_extraction(title, artist, wikipedia_entries)
    wikipedia_year = extraction_result.release_year if extraction_result else None
    wikipedia_confidence = extraction_result.confidence if extraction_result else None

    wikipedia_assisted_lock_year = _find_wikipedia_assisted_lock_year(
        [musicbrainz_year, discogs_year, wikidata_year, wikipedia_year]
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
) -> LlmExtractionResult | None:
    """Runs the dedicated reading-comprehension extraction pass over
    Wikipedia's article prose. Only called when the three structured
    sources didn't lock. Any failure is swallowed so the pipeline can
    still attempt reconciliation with whatever structured data exists."""
    try:
        extraction_prompt = build_wikipedia_extraction_prompt(title, artist, wikipedia_entries)
        return extract_structured(extraction_prompt, LlmExtractionResult)
    except Exception as extraction_error:
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
) -> LlmExtractionResult | None:
    """Runs the four-source reconciliation call that decides among
    conflicting or partial structured-source candidates. Only called when
    the three structured sources didn't lock. Any failure is swallowed."""
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
        logger.warning(
            "Four-source reconciliation failed for %r by %r: %s", title, artist, reconciliation_error
        )
        return None
