import logging

from fastapi import Depends, FastAPI

from app import prompt
from app.auth import require_internal_api_key
from app.config import settings
from app.llm import synthesize
from app.schemas import MetadataResolveRequest, MetadataResolveResponse
from app.sources import genius, musicbrainz, wikipedia, youtube
from app.sources.util import clean_youtube_text

logger = logging.getLogger(__name__)

app = FastAPI(title="hitguessr AI microservice")


def gather_all_metadata(youtube_url: str) -> dict[str, object]:
    yt_data = youtube.fetch_youtube_metadata(youtube_url)

    title = clean_youtube_text(yt_data.get("video_title"))
    artist = clean_youtube_text(yt_data.get("channel_title"))

    return {
        "youtube": yt_data,
        "musicbrainz": musicbrainz.search(title, artist),
        "wikipedia": wikipedia.search(title, artist),
        "genius": genius.search(title, artist),
    }


@app.post(
    "/metadata/resolve",
    response_model=MetadataResolveResponse,
    dependencies=[Depends(require_internal_api_key)],
)
def resolve_metadata(request: MetadataResolveRequest) -> MetadataResolveResponse:
    try:
        all_metadata = gather_all_metadata(request.youtube_url)
        built_prompt = prompt.build(all_metadata)
        result = synthesize(built_prompt)

        return MetadataResolveResponse(status="SUCCESS", model=settings.openai_model, content=result)
    except Exception as e:
        logger.warning("Metadata pipeline failed: %s", e)
        return MetadataResolveResponse(status="ERROR", model=settings.openai_model, content=None)
