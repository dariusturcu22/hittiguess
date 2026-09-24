import logging
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass

from app.config import settings
from app.dedup.embedding_client import generate_embedding
from app.dedup.normalize import normalize_artist_and_title
from app.dedup.repository import find_best_verified_match
from app.dedup.schemas import VerifiedSongMatch
from app.metadata import content_safety
from app.metadata.content_safety_prompts import build_precheck_prompt
from app.metadata.llm import extract_structured
from app.metadata.schemas import MetadataResolveResponse, SongMetadataResult, SubmissionPreCheckResult, VideoInfoItem
from app.metadata.sources import discogs, musicbrainz, wikidata, wikipedia, youtube
from app.metadata.sources.util import clean_youtube_text, extract_youtube_playlist_id, strip_featured_artist_suffix
from app.metadata.verification import (
    VerificationRoute,
    _all_three_agree,
    _extract_earliest_year,
    evaluate_lock,
    route_to_verification_status,
)

logger = logging.getLogger(__name__)

# The three structured sources take the same title and artist and share nothing,
# so the first gather runs them in a thread pool rather than one after another:
# each is network-bound and spends almost all its time blocked on an outbound
# request. One worker per source keeps the wall-clock cost bounded by the slowest
# single source instead of their sum. Each source function already isolates its
# own failures internally and returns an empty list, and a future that raises
# anyway is caught per source, so one source failing never sinks the others.
# Wikipedia is deliberately not part of this pool: it is fetched only after the
# lock check, and only when the three sources do not agree.
STRUCTURED_SOURCE_WORKER_COUNT = 3
EMPTY_SOURCE_RESULT: list = []

SUCCESS_STATUS = "SUCCESS"
REJECTED_STATUS = "REJECTED"
ERROR_STATUS = "ERROR"

# Cosine distance (0 identical, 2 opposite) below which an existing verified song counts
# as the same song under a different submission, not just a similar one. text-embedding-3-small
# clusters near-identical "artist title" strings (differing only in casing, punctuation, or minor
# wording) far tighter than this, while distinct songs land well above it; see docs/DECISIONS.md.
HIGH_CONFIDENCE_COSINE_DISTANCE_THRESHOLD = 0.08

DUPLICATE_MATCH_SOURCE_LABEL = "pgvector-duplicate-match"
DEFAULT_DUPLICATE_MATCH_CONFIDENCE = "high"
LOCKED_SOURCE_LABEL = "musicbrainz+discogs+wikidata-lock"
WIKIPEDIA_ASSISTED_LOCK_SOURCE_LABEL = "wikipedia-assisted-lock"
RECONCILED_SOURCE_LABEL = "four-source-reconciliation"
MANUAL_REVIEW_SOURCE_LABEL = "no-source-data"


def _clean_title_and_artist(youtube_data: dict[str, str]) -> tuple[str, str]:
    title = clean_youtube_text(youtube_data.get("video_title"))
    artist = clean_youtube_text(youtube_data.get("channel_title"))
    return title, artist


def _run_precheck(youtube_data: dict[str, object]) -> SubmissionPreCheckResult:
    """One combined DeepSeek call covering title/artist extraction, a
    display color, the prompt-injection check, and song/compilation
    classification, run once per submission ahead of any structured source
    query. These were three separate LLM calls (title/artist extraction,
    injection check, classification) plus a fourth gpt-5.1 call purely for
    display title/artist/color; merging them into one is a direct cost cut,
    and this one call is shared by every route, including the locked one."""
    prompt = build_precheck_prompt(
        str(youtube_data.get("video_title", "")),
        str(youtube_data.get("channel_title", "")),
        str(youtube_data.get("description", "")),
        str(youtube_data.get("category_id", "unknown")),
        youtube_data.get("duration_seconds"),
    )
    return extract_structured(prompt, SubmissionPreCheckResult)


def _fetch_source(source_search, title: str, artist: str) -> object:
    try:
        return source_search(title, artist)
    except Exception as source_error:
        logger.warning("Metadata source %s failed, continuing without it: %s", source_search.__module__, source_error)
        return EMPTY_SOURCE_RESULT


def _gather_structured_sources(title: str, artist: str) -> dict[str, list[dict]]:
    """Fetches the three structured sources (MusicBrainz, Discogs, Wikidata)
    concurrently in a thread pool. This is the first gather in the story 18
    pipeline, run before the lock check. Wikipedia is not fetched here: it is
    conditional on the lock not being met and is fetched separately afterward.
    Each source resolves through a per-source guard so one source raising does
    not sink the others."""
    source_searches = {
        "musicbrainz": musicbrainz.search,
        "discogs": discogs.search,
        "wikidata": wikidata.search,
    }
    with ThreadPoolExecutor(max_workers=STRUCTURED_SOURCE_WORKER_COUNT) as executor:
        pending = {
            source_name: executor.submit(_fetch_source, source_search, title, artist)
            for source_name, source_search in source_searches.items()
        }
        return {source_name: future.result() for source_name, future in pending.items()}


def _build_duplicate_match_result(match: VerifiedSongMatch) -> SongMetadataResult:
    return SongMetadataResult(
        title=match.title,
        main_artists=match.main_artists,
        featured_artists=match.featured_artists,
        release_year=match.release_year,
        color=match.color or "",
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
    verified song, using the free regex-cleaned title/artist: embedding
    similarity is fuzzy enough to still cluster correctly without spending
    an LLM call first just to get a more polished name. Any failure here
    (an unreachable database, an embedding API error) is swallowed and
    treated as no match, so the full pipeline below still runs rather than
    failing the whole submission over a best-effort optimization."""
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
    title: str, main_artists: list[str], color: str, featured_artists: list[str]
) -> SongMetadataResult:
    """Runs story 18's lock-or-LLM verification pipeline: query MusicBrainz,
    Discogs, and Wikidata always (free, deterministic, zero LLM cost); if
    all three agree exactly, lock with no further LLM call; otherwise fetch
    Wikipedia and run four-source reconciliation. title, main artists, and
    color already came from the shared precheck call that ran once before
    content-safety evaluation, not from a separate display-synthesis call.
    Structured sources and LLM prompts take the artists as one display
    string; the result keeps them as a list."""
    display_artists = " & ".join(main_artists)
    source_query_title = strip_featured_artist_suffix(title)
    structured_candidates = _gather_structured_sources(source_query_title, display_artists)
    musicbrainz_candidates = structured_candidates["musicbrainz"]
    discogs_candidates = structured_candidates["discogs"]
    wikidata_candidates = structured_candidates["wikidata"]

    musicbrainz_year = _extract_earliest_year(musicbrainz_candidates)
    discogs_year = _extract_earliest_year(discogs_candidates)
    wikidata_year = _extract_earliest_year(wikidata_candidates)

    wikipedia_entries: list[dict] = []
    if not _all_three_agree(musicbrainz_year, discogs_year, wikidata_year):
        wikipedia_entries = wikipedia.search(source_query_title, display_artists)

    release_year, confidence, route = evaluate_lock(
        source_query_title,
        display_artists,
        musicbrainz_candidates,
        discogs_candidates,
        wikidata_candidates,
        wikipedia_entries,
    )
    verification_status = route_to_verification_status(route)

    track_entity_id = wikidata_candidates[0].get("entity_id") if wikidata_candidates else None
    sitelinks_count = wikidata.get_sitelinks_count(track_entity_id) if track_entity_id else None

    source_label = {
        VerificationRoute.LOCKED: LOCKED_SOURCE_LABEL,
        VerificationRoute.LOCKED_WITH_WIKIPEDIA: WIKIPEDIA_ASSISTED_LOCK_SOURCE_LABEL,
        VerificationRoute.LLM_RECONCILED: RECONCILED_SOURCE_LABEL,
        VerificationRoute.MANUAL_REVIEW: MANUAL_REVIEW_SOURCE_LABEL,
    }[route]

    reasoning_by_route = {
        VerificationRoute.LOCKED: (
            f"All three structured sources (MusicBrainz, Discogs, Wikidata) agree on {release_year}. "
            "Locked with no LLM call."
        ),
        VerificationRoute.LOCKED_WITH_WIKIPEDIA: (
            f"At least three source years agree on {release_year} after Wikipedia extraction. "
            "Locked with no four-source reconciliation call."
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

    return SongMetadataResult(
        title=title,
        main_artists=main_artists,
        featured_artists=featured_artists,
        release_year=release_year,
        color=color,
        confidence=confidence,
        source=source_label,
        reasoning=reasoning_by_route[route],
        verification_status=verification_status.value,
        sitelinks_count=sitelinks_count,
    )


class InvalidPlaylistLinkError(Exception):
    """Raised when a submitted playlist link or id does not parse to a valid
    YouTube playlist id."""


def expand_playlist(playlist_url_or_id: str) -> list[str]:
    playlist_id = extract_youtube_playlist_id(playlist_url_or_id)
    if playlist_id is None:
        raise InvalidPlaylistLinkError(
            f"Not a valid YouTube playlist link or id: {playlist_url_or_id}"
        )
    return youtube.fetch_playlist_video_ids(playlist_id)


def fetch_video_info(video_ids: list[str]) -> list[VideoInfoItem]:
    """Best-effort raw title and channel name per video id, for display
    before the metadata pipeline resolves a submission. An id YouTube
    doesn't recognize, or one dropped by a failed batch call, is left out
    of the result rather than defaulted here."""
    video_info_by_id = youtube.fetch_video_titles_and_channels(video_ids)
    return [
        VideoInfoItem(video_id=video_id, title=info["title"], channel_title=info["channel_title"])
        for video_id, info in video_info_by_id.items()
    ]


@dataclass(frozen=True)
class IdentifiedSubmission:
    title: str
    main_artists: list[str]
    featured_artists: list[str]
    color: str


def identify_submission(youtube_url: str) -> MetadataResolveResponse | IdentifiedSubmission:
    """The first pass every submission takes, shared by the full pipeline and the fast
    tier: the YouTube fetch, the non-music hard filter, the verified-duplicate check,
    and the combined pre-check LLM call with its content-safety gate. Returns a final
    response when the submission is rejected or matches a verified song, and the clean
    title, artists, and color otherwise. Raises on an unexpected failure; callers turn
    that into an ERROR response."""
    youtube_data = youtube.fetch_youtube_metadata(youtube_url)
    category_id = str(youtube_data.get("category_id", "unknown"))
    duration_seconds = youtube_data.get("duration_seconds")

    if content_safety.fails_hard_filter(category_id, duration_seconds):
        return MetadataResolveResponse(
            status=REJECTED_STATUS,
            model=settings.deepinfra_model,
            content=None,
            rejection_reason=content_safety.RejectionReason.NOT_MUSIC,
            rejection_detail=content_safety.NOT_MUSIC_REJECTION_DETAIL,
        )

    regex_title, regex_artist = _clean_title_and_artist(youtube_data)

    duplicate_match_result = _check_for_duplicate(regex_title, regex_artist)
    if duplicate_match_result is not None:
        return MetadataResolveResponse(status=SUCCESS_STATUS, model=settings.deepinfra_model, content=duplicate_match_result)

    precheck = _run_precheck(youtube_data)

    content_safety_outcome = content_safety.evaluate_precheck(
        precheck, str(youtube_data.get("video_title", "")), str(youtube_data.get("channel_title", ""))
    )
    if content_safety_outcome.rejected:
        return MetadataResolveResponse(
            status=REJECTED_STATUS,
            model=settings.deepinfra_model,
            content=None,
            rejection_reason=content_safety_outcome.rejection_reason,
            rejection_detail=content_safety_outcome.rejection_detail,
        )

    return IdentifiedSubmission(
        title=precheck.title or regex_title,
        main_artists=precheck.main_artists or ([regex_artist] if regex_artist else []),
        featured_artists=precheck.featured_artists or [],
        color=precheck.color,
    )


def resolve_metadata(youtube_url: str) -> MetadataResolveResponse:
    try:
        identified = identify_submission(youtube_url)
        if isinstance(identified, MetadataResolveResponse):
            return identified

        result = _run_verification_pipeline(
            identified.title, identified.main_artists, identified.color, identified.featured_artists
        )
        return MetadataResolveResponse(status=SUCCESS_STATUS, model=settings.deepinfra_model, content=result)
    except Exception as pipeline_error:
        logger.warning("Metadata pipeline failed: %s", pipeline_error)
        return MetadataResolveResponse(status=ERROR_STATUS, model=settings.deepinfra_model, content=None)
