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

logger = logging.getLogger(__name__)

# Cosine distance (0 identical, 2 opposite) below which an existing verified song counts
# as the same song under a different submission, not just a similar one. text-embedding-3-small
# clusters near-identical "artist title" strings (differing only in casing, punctuation, or minor
# wording) far tighter than this, while distinct songs land well above it; see docs/DECISIONS.md.
HIGH_CONFIDENCE_COSINE_DISTANCE_THRESHOLD = 0.08

DUPLICATE_MATCH_SOURCE_LABEL = "pgvector-duplicate-match"
DEFAULT_DUPLICATE_MATCH_CONFIDENCE = "high"


def _clean_title_and_artist(youtube_data: dict[str, str]) -> tuple[str, str]:
    title = clean_youtube_text(youtube_data.get("video_title"))
    artist = clean_youtube_text(youtube_data.get("channel_title"))
    return title, artist


def _gather_all_metadata(youtube_data: dict[str, str], title: str, artist: str) -> dict[str, object]:
    return {
        "youtube": youtube_data,
        "musicbrainz": musicbrainz.search(title, artist),
        "discogs": discogs.search(title, artist),
        "wikidata": wikidata.search(title, artist),
        "wikipedia": wikipedia.search(title, artist),
    }


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


def resolve_metadata(youtube_url: str) -> MetadataResolveResponse:
    try:
        youtube_data = youtube.fetch_youtube_metadata(youtube_url)
        title, artist = _clean_title_and_artist(youtube_data)

        duplicate_match_result = _check_for_duplicate(title, artist)
        if duplicate_match_result is not None:
            return MetadataResolveResponse(status="SUCCESS", model=settings.openai_model, content=duplicate_match_result)

        all_metadata = _gather_all_metadata(youtube_data, title, artist)
        built_prompt = prompt.build(all_metadata)
        result = synthesize(built_prompt)

        return MetadataResolveResponse(status="SUCCESS", model=settings.openai_model, content=result)
    except Exception as e:
        logger.warning("Metadata pipeline failed: %s", e)
        return MetadataResolveResponse(status="ERROR", model=settings.openai_model, content=None)
