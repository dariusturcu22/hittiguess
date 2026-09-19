from fastapi import APIRouter, Depends, HTTPException, status

from app.auth import require_internal_api_key
from app.metadata.schemas import (
    MetadataResolveRequest,
    MetadataResolveResponse,
    PlaylistVideoIdsRequest,
    PlaylistVideoIdsResponse,
)
from app.metadata.service import InvalidPlaylistLinkError, expand_playlist, resolve_metadata
from app.metadata.sources.youtube import PlaylistFetchError
from app.rate_limit import enforce_metadata_resolve_rate_limit

router = APIRouter(
    prefix="/metadata",
    tags=["metadata"],
    dependencies=[Depends(enforce_metadata_resolve_rate_limit), Depends(require_internal_api_key)],
)


@router.post("/resolve", response_model=MetadataResolveResponse)
def resolve(request: MetadataResolveRequest) -> MetadataResolveResponse:
    return resolve_metadata(request.youtube_url)


@router.post("/playlist-video-ids", response_model=PlaylistVideoIdsResponse)
def playlist_video_ids(request: PlaylistVideoIdsRequest) -> PlaylistVideoIdsResponse:
    try:
        video_ids = expand_playlist(request.playlist_url_or_id)
    except InvalidPlaylistLinkError as invalid_playlist_link_error:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail=str(invalid_playlist_link_error)
        ) from invalid_playlist_link_error
    except PlaylistFetchError as playlist_fetch_error:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY, detail=str(playlist_fetch_error)
        ) from playlist_fetch_error

    return PlaylistVideoIdsResponse(video_ids=video_ids)
