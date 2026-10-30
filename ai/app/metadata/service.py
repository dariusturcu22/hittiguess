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
    VerificationStatus,
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

MUSICBRAINZ_SOURCE = "musicbrainz"
DISCOGS_SOURCE = "discogs"
WIKIDATA_SOURCE = "wikidata"
STRUCTURED_SOURCE_MODULES = {MUSICBRAINZ_SOURCE: musicbrainz, DISCOGS_SOURCE: discogs, WIKIDATA_SOURCE: wikidata}

SUCCESS_STATUS = "SUCCESS"
REJECTED_STATUS = "REJECTED"
ERROR_STATUS = "ERROR"

# Cosine distance (0 identical, 2 opposite) below which an existing verified song counts
# as the same song under a different submission, not just a similar one. text-embedding-3-small
# clusters near-identical "artist title" strings (differing only in casing, punctuation, or minor
# wording) far tighter than this, while distinct songs land well above it; see docs/DECISIONS.md.
HIGH_CONFIDENCE_COSINE_DISTANCE_THRESHOLD = 0.08

DUPLICATE_MATCH_SOURCE_LABEL = "pgvector-duplicate-match"
VERIFIED_STATUS = "VERIFIED"
DEFAULT_DUPLICATE_MATCH_CONFIDENCE = "high"
LOCKED_SOURCE_LABEL = "musicbrainz+discogs+wikidata-lock"
WIKIPEDIA_ASSISTED_LOCK_SOURCE_LABEL = "wikipedia-assisted-lock"
RECONCILED_SOURCE_LABEL = "four-source-reconciliation"
MANUAL_REVIEW_SOURCE_LABEL = "no-source-data"
UPLOAD_YEAR_SOURCE_LABEL = "youtube-upload-year"
UPLOAD_YEAR_CONFIDENCE = "low"


def _clean_title_and_artist(youtube_data: dict[str, str]) -> tuple[str, str]:
    title = clean_youtube_text(youtube_data.get("video_title"))
    artist = clean_youtube_text(youtube_data.get("channel_title"))
    return title, artist


def _run_precheck(youtube_data: dict[str, object]) -> SubmissionPreCheckResult:
    """Extract display metadata and classify submission safety in one structured
    precheck before querying metadata sources, including on locked routes.
    """
    prompt = build_precheck_prompt(
        str(youtube_data.get("video_title", "")),
        str(youtube_data.get("channel_title", "")),
        str(youtube_data.get("description", "")),
        str(youtube_data.get("category_id", "unknown")),
        youtube_data.get("duration_seconds"),
    )
    return extract_structured(prompt, SubmissionPreCheckResult)


@dataclass(frozen=True)
class SourceStageResult:
    """One source's answer: its candidates as the source module returns them and the
    earliest year among them, the value the lock check compares. sitelinks_count is
    only looked up for Wikidata."""

    candidates: list[dict]
    earliest_year: int | None
    sitelinks_count: int | None = None


def _source_query(title: str, main_artists: list[str]) -> tuple[str, list[str]]:
    """Sources are queried without a featured-artist suffix on the title and
    receive the main artists as a list, so each source can choose its own
    query form."""
    return strip_featured_artist_suffix(title), list(main_artists)


def _fetch_source(source_search, title: str, artist_query: str | list[str]) -> object:
    try:
        return source_search(title, artist_query)
    except Exception as source_error:
        logger.warning("Metadata source %s failed, continuing without it: %s", source_search.__module__, source_error)
        return EMPTY_SOURCE_RESULT


def _track_sitelinks_count(wikidata_candidates: list[dict]) -> int | None:
    track_entity_id = wikidata_candidates[0].get("entity_id") if wikidata_candidates else None
    return wikidata.get_sitelinks_count(track_entity_id) if track_entity_id else None


def run_structured_source_stage(
    source_name: str, title: str, main_artists: list[str], *, raise_on_failure: bool = False
) -> SourceStageResult:
    """Queries one structured source (MusicBrainz, Discogs, or Wikidata) for the
    precheck's title and main artists. A failing source counts as one with no
    candidates, unless raise_on_failure is set, in which case its error propagates."""
    source_query_title, main_artist_names = _source_query(title, main_artists)
    source_module = STRUCTURED_SOURCE_MODULES[source_name]
    source_artist_query: list[str] = main_artist_names
    if raise_on_failure:
        candidates = source_module.search_or_raise(source_query_title, source_artist_query)
    else:
        candidates = _fetch_source(source_module.search, source_query_title, source_artist_query)
    sitelinks_count = _track_sitelinks_count(candidates) if source_name == WIKIDATA_SOURCE else None
    return SourceStageResult(
        candidates=candidates, earliest_year=_extract_earliest_year(candidates), sitelinks_count=sitelinks_count
    )


def run_wikipedia_stage(title: str, main_artists: list[str], *, raise_on_failure: bool = False) -> list[dict]:
    """Fetches Wikipedia's lead-section entries, the input the reconciliation stage's
    extraction call reads. Only needed when the structured sources do not agree."""
    source_query_title, main_artist_names = _source_query(title, main_artists)
    display_artists = " & ".join(main_artist_names)
    if raise_on_failure:
        return wikipedia.search_or_raise(source_query_title, display_artists)
    return wikipedia.search(source_query_title, display_artists)


def structured_sources_agree(
    musicbrainz_year: int | None, discogs_year: int | None, wikidata_year: int | None
) -> bool:
    return _all_three_agree(musicbrainz_year, discogs_year, wikidata_year)


def _gather_structured_sources(title: str, main_artists: list[str]) -> dict[str, SourceStageResult]:
    """Runs the three structured source stages (MusicBrainz, Discogs, Wikidata)
    concurrently in a thread pool. This is the first gather in the story 18
    pipeline, run before the lock check. Wikipedia is not fetched here: it is
    conditional on the lock not being met and is fetched separately afterward.
    Each source resolves through a per-source guard so one source raising does
    not sink the others."""
    with ThreadPoolExecutor(max_workers=STRUCTURED_SOURCE_WORKER_COUNT) as executor:
        pending = {
            source_name: executor.submit(run_structured_source_stage, source_name, title, main_artists)
            for source_name in STRUCTURED_SOURCE_MODULES
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
        verification_status=VERIFIED_STATUS,
        canonical_song_id=match.id,
    )


def _check_for_duplicate(title: str, artist: str, youtube_id: str | None = None) -> SongMetadataResult | None:
    """Looks up whether this submission is a near-duplicate of an already
    verified song, using the free regex-cleaned title/artist: embedding
    similarity is fuzzy enough to still cluster correctly without spending
    an LLM call first just to get a more polished name. Any failure here
    (an unreachable database, an embedding API error) is swallowed and
    treated as no match, so the full pipeline below still runs rather than
    failing the whole submission over a best-effort optimization. The song that
    already owns youtube_id is never the match."""
    try:
        normalized_text = normalize_artist_and_title(artist, title)
        embedding = generate_embedding(normalized_text)
        best_match = find_best_verified_match(embedding, excluded_youtube_id=youtube_id)
    except Exception as duplicate_check_error:
        logger.warning("Duplicate-detection check failed, proceeding with the full pipeline: %s", duplicate_check_error)
        return None

    if best_match is None or best_match.cosine_distance > HIGH_CONFIDENCE_COSINE_DISTANCE_THRESHOLD:
        return None

    return _build_duplicate_match_result(best_match)


def run_reconciliation_stage(
    title: str,
    main_artists: list[str],
    featured_artists: list[str],
    color: str,
    musicbrainz_candidates: list[dict],
    discogs_candidates: list[dict],
    wikidata_candidates: list[dict],
    wikipedia_entries: list[dict],
    sitelinks_count: int | None,
    *,
    raise_on_failure: bool = False,
    upload_year: int | None = None,
) -> SongMetadataResult:
    """The finishing step of story 18's lock-or-LLM verification: when the three
    structured sources agree exactly, locks with no LLM call; otherwise reads
    Wikipedia with the extraction call and runs four-source reconciliation.
    title, main artists, and color come from the precheck call, not from a
    separate display-synthesis call. When no source has any year, the video's upload
    year is proposed for review instead of leaving the song without a year: an obscure
    or recent song's official upload is usually its release."""
    source_query_title, main_artist_names = _source_query(title, main_artists)
    display_artists = " & ".join(main_artist_names)
    release_year, confidence, route = evaluate_lock(
        source_query_title,
        display_artists,
        musicbrainz_candidates,
        discogs_candidates,
        wikidata_candidates,
        wikipedia_entries,
        raise_on_failure=raise_on_failure,
    )
    verification_status = route_to_verification_status(route)
    if route == VerificationRoute.MANUAL_REVIEW and upload_year is not None:
        return SongMetadataResult(
            title=title,
            main_artists=main_artists,
            featured_artists=featured_artists,
            release_year=upload_year,
            color=color,
            confidence=UPLOAD_YEAR_CONFIDENCE,
            source=UPLOAD_YEAR_SOURCE_LABEL,
            reasoning=(
                f"No source, including Wikipedia, returned a year. The video was uploaded in {upload_year}, "
                "proposed for review as the release year."
            ),
            verification_status=VerificationStatus.NEEDS_REVIEW.value,
            sitelinks_count=sitelinks_count,
        )

    source_label = {
        VerificationRoute.LOCKED: LOCKED_SOURCE_LABEL,
        VerificationRoute.LOCKED_WITH_WIKIPEDIA: WIKIPEDIA_ASSISTED_LOCK_SOURCE_LABEL,
        VerificationRoute.LLM_RECONCILED: RECONCILED_SOURCE_LABEL,
        VerificationRoute.MANUAL_REVIEW: MANUAL_REVIEW_SOURCE_LABEL,
    }[route]

    reasoning_by_route = {
        VerificationRoute.LOCKED: (
            f"All three structured sources (MusicBrainz, Discogs, Wikidata) agree on {release_year}. "
            "Locked without Wikipedia extraction or year reconciliation."
        ),
        VerificationRoute.LOCKED_WITH_WIKIPEDIA: (
            f"At least two sources support the earliest year {release_year} after Wikipedia extraction. "
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


def _run_verification_pipeline(
    title: str, main_artists: list[str], color: str, featured_artists: list[str]
) -> SongMetadataResult:
    """Runs story 18's lock-or-LLM verification pipeline from its stages: the three
    structured sources always (free, deterministic, zero LLM cost), Wikipedia only
    when they do not agree exactly, then the reconciliation stage."""
    structured_results = _gather_structured_sources(title, main_artists)
    musicbrainz_result = structured_results[MUSICBRAINZ_SOURCE]
    discogs_result = structured_results[DISCOGS_SOURCE]
    wikidata_result = structured_results[WIKIDATA_SOURCE]

    wikipedia_entries: list[dict] = []
    if not structured_sources_agree(
        musicbrainz_result.earliest_year, discogs_result.earliest_year, wikidata_result.earliest_year
    ):
        wikipedia_entries = run_wikipedia_stage(title, main_artists)

    return run_reconciliation_stage(
        title,
        main_artists,
        featured_artists,
        color,
        musicbrainz_result.candidates,
        discogs_result.candidates,
        wikidata_result.candidates,
        wikipedia_entries,
        wikidata_result.sitelinks_count,
    )


def finish_resolution(result: SongMetadataResult, duration_seconds: int | None) -> MetadataResolveResponse:
    """The successful response for a verified result, carrying the submitted upload's duration."""
    result = result.model_copy(update={"duration_seconds": duration_seconds})
    return MetadataResolveResponse(status=SUCCESS_STATUS, model=settings.deepinfra_model, content=result)


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
    duration_seconds: int | None = None


def identify_from_youtube_data(
    youtube_data: dict[str, object], youtube_id: str | None = None
) -> MetadataResolveResponse | IdentifiedSubmission:
    """The first stage every submission takes once its YouTube metadata is fetched,
    shared by the full pipeline, the stage endpoints, and the fast tier: the non-music
    hard filter, the verified-duplicate check, and the combined pre-check LLM call
    with its content-safety gate. Returns a final response when the submission is
    rejected or matches a verified song, and the clean title, artists, and color
    otherwise. youtube_id, when given, keeps the duplicate check from matching the
    song that already owns that video, so a rerun resolves the song again. Raises when
    the precheck call fails."""
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

    duplicate_match_result = _check_for_duplicate(regex_title, regex_artist, youtube_id)
    if duplicate_match_result is not None:
        duplicate_match_result = duplicate_match_result.model_copy(update={"duration_seconds": duration_seconds})
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
        duration_seconds=duration_seconds,
    )


def identify_submission(youtube_url: str) -> MetadataResolveResponse | IdentifiedSubmission:
    """identify_from_youtube_data on a fresh YouTube fetch. A failed fetch falls back
    to unknown defaults. Raises on an unexpected failure; callers turn that into an
    ERROR response."""
    return identify_from_youtube_data(youtube.fetch_youtube_metadata(youtube_url))


def resolve_metadata(youtube_url: str) -> MetadataResolveResponse:
    try:
        identified = identify_submission(youtube_url)
        if isinstance(identified, MetadataResolveResponse):
            return identified

        result = _run_verification_pipeline(
            identified.title, identified.main_artists, identified.color, identified.featured_artists
        )
        return finish_resolution(result, identified.duration_seconds)
    except Exception as pipeline_error:
        logger.warning("Metadata pipeline failed: %s", pipeline_error)
        return MetadataResolveResponse(status=ERROR_STATUS, model=settings.deepinfra_model, content=None)
