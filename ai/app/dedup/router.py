from fastapi import APIRouter, Depends

from app.auth import require_internal_api_key
from app.dedup.indexing import IndexingResult, index_next_song

router = APIRouter(prefix="/catalog", dependencies=[Depends(require_internal_api_key)])


@router.post("/index-next", response_model=IndexingResult)
def index_catalog_song() -> IndexingResult:
    return index_next_song()
