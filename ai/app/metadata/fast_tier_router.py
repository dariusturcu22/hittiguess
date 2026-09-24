from fastapi import APIRouter, Depends

from app.auth import require_internal_api_key
from app.metadata.fast_tier import date_fast, identify
from app.metadata.schemas import FastDateRequest, FastDateResponse, IdentifyRequest, IdentifyResponse
from app.rate_limit import enforce_fast_tier_rate_limit

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
