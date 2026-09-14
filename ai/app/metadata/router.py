from fastapi import APIRouter, Depends

from app.auth import require_internal_api_key
from app.metadata.schemas import MetadataResolveRequest, MetadataResolveResponse
from app.metadata.service import resolve_metadata
from app.rate_limit import enforce_metadata_resolve_rate_limit

router = APIRouter(
    prefix="/metadata",
    tags=["metadata"],
    dependencies=[Depends(enforce_metadata_resolve_rate_limit), Depends(require_internal_api_key)],
)


@router.post("/resolve", response_model=MetadataResolveResponse)
def resolve(request: MetadataResolveRequest) -> MetadataResolveResponse:
    return resolve_metadata(request.youtube_url)
