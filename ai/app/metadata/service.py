import logging

from app.config import settings
from app.metadata import prompt
from app.metadata.llm import synthesize
from app.metadata.schemas import MetadataResolveResponse
from app.metadata.sources import genius, musicbrainz, wikipedia, youtube
from app.metadata.sources.util import clean_youtube_text

logger = logging.getLogger(__name__)


def _gather_all_metadata(youtube_url: str) -> dict[str, object]:
    yt_data = youtube.fetch_youtube_metadata(youtube_url)

    title = clean_youtube_text(yt_data.get("video_title"))
    artist = clean_youtube_text(yt_data.get("channel_title"))

    return {
        "youtube": yt_data,
        "musicbrainz": musicbrainz.search(title, artist),
        "wikipedia": wikipedia.search(title, artist),
        "genius": genius.search(title, artist),
    }


def resolve_metadata(youtube_url: str) -> MetadataResolveResponse:
    try:
        all_metadata = _gather_all_metadata(youtube_url)
        built_prompt = prompt.build(all_metadata)
        result = synthesize(built_prompt)

        return MetadataResolveResponse(status="SUCCESS", model=settings.openai_model, content=result)
    except Exception as e:
        logger.warning("Metadata pipeline failed: %s", e)
        return MetadataResolveResponse(status="ERROR", model=settings.openai_model, content=None)
