"""The metadata pipeline as one endpoint per stage, for core's stage queues. Each runs
the same stage function /metadata/resolve is composed from. A source whose next free
slot is more than MAXIMUM_STAGE_PACE_WAIT_SECONDS away is not waited for: the call
returns 429 with retryAfterSeconds instead."""

from collections.abc import Callable
from typing import TypeVar

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse

from app.auth import require_internal_api_key
from app.metadata import service
from app.metadata.schemas import MetadataResolveResponse
from app.metadata.sources import youtube
from app.metadata.sources.pace_state import (
    STAGE_DISCOGS,
    STAGE_MUSICBRAINZ,
    STAGE_WIKIDATA,
    STAGE_WIKIPEDIA,
    STAGE_YOUTUBE_METADATA,
    PaceWaitTooLong,
)
from app.metadata.sources.pacing import waits_capped_at
from app.metadata.stages.errors import StageFailure, failure_response, failures_attributed_to, pace_limit_response
from app.metadata.stages.schemas import (
    ReconcileStageRequest,
    SourceStageRequest,
    StageProvider,
    StructuredSourceStageResponse,
    VideoFacts,
    WikidataStageResponse,
    WikipediaStageResponse,
    YoutubeMetadataStageRequest,
    YoutubeMetadataStageResponse,
    YoutubeStageOutcome,
)

MAXIMUM_STAGE_PACE_WAIT_SECONDS = 30.0
STAGE_LLM_RECONCILIATION = "LLM_RECONCILIATION"
ARTIST_SEPARATOR = " & "

router = APIRouter(prefix="/stages", tags=["stages"], dependencies=[Depends(require_internal_api_key)])

StageResult = TypeVar("StageResult")


def _run_stage(
    stage: str, run: Callable[[], StageResult], title: str | None, artist: str | None
) -> StageResult | JSONResponse:
    try:
        with waits_capped_at(MAXIMUM_STAGE_PACE_WAIT_SECONDS):
            return run()
    except PaceWaitTooLong as pace_wait:
        return pace_limit_response(stage, pace_wait)
    except StageFailure as failure:
        return failure_response(stage, failure, title, artist)
    except Exception as error:
        return failure_response(stage, StageFailure(None, error), title, artist)


def _video_facts(youtube_data: dict[str, object]) -> VideoFacts:
    return VideoFacts(
        video_title=str(youtube_data.get("video_title", "")),
        channel_title=str(youtube_data.get("channel_title", "")),
        duration_seconds=youtube_data.get("duration_seconds"),
        category_id=str(youtube_data.get("category_id", "")),
        upload_year=_upload_year(youtube_data),
    )


def _upload_year(youtube_data: dict[str, object]) -> int | None:
    upload_year = str(youtube_data.get("upload_year", ""))
    return int(upload_year) if upload_year.isdigit() else None


def _youtube_stage_response(
    youtube_data: dict[str, object], outcome: MetadataResolveResponse | service.IdentifiedSubmission
) -> YoutubeMetadataStageResponse:
    video = _video_facts(youtube_data)
    if isinstance(outcome, service.IdentifiedSubmission):
        return YoutubeMetadataStageResponse(
            outcome=YoutubeStageOutcome.IDENTIFIED,
            video=video,
            title=outcome.title,
            artists=outcome.main_artists,
            featured_artists=outcome.featured_artists,
            color=outcome.color,
        )
    if outcome.status == service.REJECTED_STATUS:
        return YoutubeMetadataStageResponse(
            outcome=YoutubeStageOutcome.REJECTED,
            video=video,
            rejection_reason=outcome.rejection_reason,
            rejection_detail=outcome.rejection_detail,
            result=outcome,
        )
    duplicate = outcome.content
    return YoutubeMetadataStageResponse(
        outcome=YoutubeStageOutcome.DUPLICATE,
        video=video,
        title=duplicate.title,
        artists=duplicate.main_artists,
        featured_artists=duplicate.featured_artists,
        color=duplicate.color,
        result=outcome,
    )


@router.post("/youtube-metadata", response_model=YoutubeMetadataStageResponse)
def youtube_metadata_stage(request: YoutubeMetadataStageRequest):
    def run() -> YoutubeMetadataStageResponse:
        with failures_attributed_to(StageProvider.YOUTUBE):
            youtube_data = youtube.fetch_youtube_metadata_or_raise(request.youtube_id)
        with failures_attributed_to(StageProvider.DEEPINFRA):
            outcome = service.identify_from_youtube_data(youtube_data, request.youtube_id)
        return _youtube_stage_response(youtube_data, outcome)

    return _run_stage(STAGE_YOUTUBE_METADATA, run, request.youtube_id, None)


def _structured_source_stage(
    stage: str, source_name: str, provider: StageProvider, request: SourceStageRequest
) -> service.SourceStageResult | JSONResponse:
    def run() -> service.SourceStageResult:
        with failures_attributed_to(provider):
            return service.run_structured_source_stage(
                source_name, request.title, request.artists, raise_on_failure=True
            )

    return _run_stage(stage, run, request.title, ARTIST_SEPARATOR.join(request.artists))


def _structured_source_response(result: service.SourceStageResult | JSONResponse):
    if isinstance(result, JSONResponse):
        return result
    return StructuredSourceStageResponse(candidates=result.candidates, earliest_year=result.earliest_year)


@router.post("/musicbrainz", response_model=StructuredSourceStageResponse)
def musicbrainz_stage(request: SourceStageRequest):
    return _structured_source_response(
        _structured_source_stage(STAGE_MUSICBRAINZ, service.MUSICBRAINZ_SOURCE, StageProvider.MUSICBRAINZ, request)
    )


@router.post("/discogs", response_model=StructuredSourceStageResponse)
def discogs_stage(request: SourceStageRequest):
    return _structured_source_response(
        _structured_source_stage(STAGE_DISCOGS, service.DISCOGS_SOURCE, StageProvider.DISCOGS, request)
    )


@router.post("/wikidata", response_model=WikidataStageResponse)
def wikidata_stage(request: SourceStageRequest):
    result = _structured_source_stage(STAGE_WIKIDATA, service.WIKIDATA_SOURCE, StageProvider.WIKIDATA, request)
    if isinstance(result, JSONResponse):
        return result
    return WikidataStageResponse(
        candidates=result.candidates, earliest_year=result.earliest_year, sitelinks_count=result.sitelinks_count
    )


@router.post("/wikipedia", response_model=WikipediaStageResponse)
def wikipedia_stage(request: SourceStageRequest):
    def run() -> WikipediaStageResponse:
        with failures_attributed_to(StageProvider.WIKIPEDIA):
            entries = service.run_wikipedia_stage(request.title, request.artists, raise_on_failure=True)
        return WikipediaStageResponse(candidates=entries)

    return _run_stage(STAGE_WIKIPEDIA, run, request.title, ARTIST_SEPARATOR.join(request.artists))


@router.post("/llm-reconcile", response_model=MetadataResolveResponse)
def llm_reconcile_stage(request: ReconcileStageRequest):
    """Always callable to finish a run: three agreeing years lock with no LLM call."""

    def run() -> MetadataResolveResponse:
        result = service.run_reconciliation_stage(
            request.title,
            request.artists,
            request.featured_artists,
            request.color,
            request.musicbrainz_candidates,
            request.discogs_candidates,
            request.wikidata_candidates,
            request.wikipedia_candidates,
            request.sitelinks_count,
            raise_on_failure=True,
            upload_year=request.video.upload_year,
        )
        return service.finish_resolution(result, request.video.duration_seconds)

    return _run_stage(STAGE_LLM_RECONCILIATION, run, request.title, ARTIST_SEPARATOR.join(request.artists))
