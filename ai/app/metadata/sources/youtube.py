import httpx

from app.config import settings
from app.metadata.sources.util import build_youtube_api_url, extract_youtube_video_id

UNKNOWN_DEFAULTS = {
    "channel_title": "unknown",
    "video_title": "unknown",
    "description": "",
    "upload_date": "unknown",
    "upload_year": "unknown",
    "tags": "[]",
}


def fetch_youtube_metadata(url: str) -> dict[str, str]:
    try:
        video_id = extract_youtube_video_id(url)
        api_url = build_youtube_api_url(video_id, settings.youtube_api_key)

        response = httpx.get(api_url, timeout=5.0)
        response.raise_for_status()
        items = response.json().get("items", [])

        if not items:
            return dict(UNKNOWN_DEFAULTS)

        snippet = items[0].get("snippet", {})
        published_at = snippet.get("publishedAt", "unknown")

        return {
            "channel_title": snippet.get("channelTitle", "unknown"),
            "video_title": snippet.get("title", "unknown"),
            "description": snippet.get("description", ""),
            "tags": str(snippet.get("tags", [])),
            "upload_date": published_at[:10] if len(published_at) >= 10 else "unknown",
            "upload_year": published_at[:4] if len(published_at) >= 4 else "unknown",
        }
    except Exception:
        return dict(UNKNOWN_DEFAULTS)
