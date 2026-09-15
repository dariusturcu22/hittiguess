import logging

from app.config import settings
from app.dedup.embedding_client import generate_embedding
from app.dedup.normalize import normalize_artist_and_title
from app.dedup.repository import find_best_verified_match
from app.dedup.schemas import VerifiedSongMatch
from app.metadata import prompt
from app.metadata.llm import synthesize
from app.metadata.schemas import MetadataResolveResponse, SongMetadataResult
from app.metadata.sources import discogs, musicbrainz, wikidata, wikipedia, youtube
from app.metadata.sources.util import clean_youtube_text
from app.metadata.verification import (
    VerificationRoute,
    _all_three_agree,
    _extract_earliest_year,
    evaluate_lock,
    route_to_verification_status,
)

logger = logging.getLogger(__name__)

HIGH_CONFIDENCE_COSINE_DISTANCE_THRESHOLD = 0.08

DUPLICATE_MATCH_SOURCE_LABEL = "pgvector-duplicate-match"
DEFAULT_DUPLICATE_MATCH_CONFIDENCE = "high"
LOCKED_SOURCE_LABEL = "musicbrainz+discogs+wikidata-lock"
RECONCILED_SOURCE_LABEL = "four-source-reconciliation"
MANUAL_REVIEW_SOURCE_LABEL = "no-source-data"


def _clean_title_and_artist(youtube_data: dict[str, str]) -> tuple[str, str]:
    title = clean_youtube_text(youtube_data.get("video_title"))
    artist = clean_youtube_text(youtube_data.get("channel_title"))
    return title, artist


def _build_duplicate_match_result(match: VerifiedSongMatch) -> SongMetadataResult:
    return SongMetadataResult(
        title=match.title,
        artist=match.artist,
        release_year=match.release_year,
        gradient_color1=match.gradient_color1 or "",
        gradient_color2=match.gradient_color2 or "",
        confidence=match.confidence or DEFAULT_DUPLICATE_MATCH_CONFIDENCE,
        source=DUPLICATE_MATCH_SOURCE_LABEL,
        reasoning=(
            f"Matched existing verified song {match.id} by cosine distance "
            f"{match.cosine_distance:.4f}, at or below the "
            f"{HIGH_CONFIDENCE_COSINE_DISTANCE_THRESHOLD} high-confidence threshold. "
            "Reused its data instead of re-running the metadata pipeline."
        ),
        verification_status=None,
    )


def _check_for_duplicate(title: str, artist: str) -> SongMetadataResult | None:
    """Looks up whether this submission is a near-duplicate of an already
    verified song. Any failure here (an unreachable database, an embedding
    API error) is swallowed and treated as no match, so the full pipeline
    below still runs rather than failing the whole submission over a
    best-effort optimization."""
    try:
        normalized_text = normalize_artist_and_title(artist, title)
        embedding = generate_embedding(normalized_text)
        best_match = find_best_verified_match(embedding)
    except Exception as duplicate_check_error:
        logger.warning("Duplicate-detection check failed, proceeding with the full pipeline: %s", duplicate_check_error)
        return None

    if best_match is None or best_match.cosine_distance > HIGH_CONFIDENCE_COSINE_DISTANCE_THRESHOLD:
        return None

    return _build_duplicate_match_result(best_match)


def _run_verification_pipeline(
    youtube_data: dict[str, str],
    title: str,
    artist: str,
) -> SongMetadataResult:
    """Runs story 18's lock-or-LLM verification pipeline alongside the
    existing synthesize call (which handles title, artist, and gradient
    colors). The verification pipeline determines release_year, confidence,
    source, reasoning, and verification_status; synthesize's release_year
    is discarded in favor of the verified one.

    Wikipedia is fetched only when the three structured sources don't lock,
    matching the conditional design that keeps 53% of songs LLM-free.
    The synthesize call remains for gradient colors until story 40
    restructures the full submission pipeline."""
    musicbrainz_candidates = musicbrainz.search(title, artist)
    discogs_candidates = discogs.search(title, artist)
    wikidata_candidates = wikidata.search(title, artist)

    musicbrainz_year = _extract_earliest_year(musicbrainz_candidates)
    discogs_year = _extract_earliest_year(discogs_candidates)
    wikidata_year = _extract_earliest_year(wikidata_candidates)

    wikipedia_entries: list[dict] = []
    if not _all_three_agree(musicbrainz_year, discogs_year, wikidata_year):
        wikipedia_entries = wikipedia.search(title, artist)

    release_year, confidence, route = evaluate_lock(
        title,
        artist,
        musicbrainz_candidates,
        discogs_candidates,
        wikidata_candidates,
        wikipedia_entries,
    )
    verification_status = route_to_verification_status(route)

    source_label = {
        VerificationRoute.LOCKED: LOCKED_SOURCE_LABEL,
        VerificationRoute.LLM_RECONCILED: RECONCILED_SOURCE_LABEL,
        VerificationRoute.MANUAL_REVIEW: MANUAL_REVIEW_SOURCE_LABEL,
    }[route]

    reasoning_by_route = {
        VerificationRoute.LOCKED: (
            f"All three structured sources (MusicBrainz, Discogs, Wikidata) agree on {release_year}. "
            "Locked with no LLM call."
        ),
        VerificationRoute.LLM_RECONCILED: (
            "Structured sources disagreed or one or more returned no data. "
            f"Wikipedia was fetched and four-source reconciliation produced {release_year}."
        ),
        VerificationRoute.MANUAL_REVIEW: (
            "No source, including Wikipedia, returned any data for this song. "
            "Routes to manual review for human entry of the release year."
        ),
    }

    all_metadata = {
        "youtube": youtube_data,
        "musicbrainz": musicbrainz_candidates,
        "discogs": discogs_candidates,
        "wikidata": wikidata_candidates,
        "wikipedia": wikipedia_entries,
    }
    built_prompt = prompt.build(all_metadata)
    display_result = synthesize(built_prompt)

    return SongMetadataResult(
        title=display_result.title,
        artist=display_result.artist,
        release_year=release_year,
        gradient_color1=display_result.gradient_color1,
        gradient_color2=display_result.gradient_color2,
        confidence=confidence,
        source=source_label,
        reasoning=reasoning_by_route[route],
        verification_status=verification_status.value,
    )


def resolve_metadata(youtube_url: str) -> MetadataResolveResponse:
    try:
        youtube_data = youtube.fetch_youtube_metadata(youtube_url)
        title, artist = _clean_title_and_artist(youtube_data)

        duplicate_match_result = _check_for_duplicate(title, artist)
        if duplicate_match_result is not None:
            return MetadataResolveResponse(status="SUCCESS", model=settings.openai_model, content=duplicate_match_result)

        result = _run_verification_pipeline(youtube_data, title, artist)
        return MetadataResolveResponse(status="SUCCESS", model=settings.openai_model, content=result)
    except Exception as pipeline_error:
        logger.warning("Metadata pipeline failed: %s", pipeline_error)
        return MetadataResolveResponse(status="ERROR", model=settings.openai_model, content=None)
