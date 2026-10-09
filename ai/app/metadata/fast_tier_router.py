from fastapi import APIRouter, Depends, HTTPException, status
import httpx

from app.auth import require_internal_api_key
from app.metadata.fast_tier import date_fast, identify
from app.metadata.schemas import FastDateRequest, FastDateResponse, IdentifyRequest, IdentifyResponse
from app.rate_limit import enforce_fast_tier_rate_limit
from app.metadata.schemas import VideoDurationsRequest, VideoDurationsResponse
from app.metadata.sources.youtube import fetch_video_durations

router = APIRouter(
    prefix="/metadata",
    tags=["metadata-fast-tier"],
    dependencies=[Depends(enforce_fast_tier_rate_limit), Depends(require_internal_api_key)],
)


@router.post("/identify", response_model=IdentifyResponse)
def identify_video(request: IdentifyRequest) -> IdentifyResponse:
    return identify(request.youtube_url)


@router.post("/date-fast", response_model=FastDateResponse)
def date_video_fast(request: FastDateRequest) -> FastDateResponse:
    return date_fast(request.title, request.main_artists)


@router.post("/video-durations", response_model=VideoDurationsResponse)
def video_durations(request: VideoDurationsRequest) -> VideoDurationsResponse:
    try:
        return VideoDurationsResponse(durations=fetch_video_durations(request.video_ids))
    except (httpx.HTTPError, ValueError, KeyError, TypeError) as lookup_error:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="YouTube duration lookup failed") from lookup_error
