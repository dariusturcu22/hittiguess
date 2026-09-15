import httpx

from app.config import settings
from app.metadata.sources.util import (
    build_youtube_api_url,
    extract_youtube_video_id,
    parse_iso8601_duration_seconds,
)
from app.observability.error_reporting import report_source_failure

SOURCE_NAME = "youtube"
UNKNOWN_DEFAULTS = {
    "channel_title": "unknown",
    "video_title": "unknown",
    "description": "",
    "upload_date": "unknown",
    "upload_year": "unknown",
    "tags": "[]",
    "category_id": "unknown",
    "duration_seconds": None,
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

        first_item = items[0]
        snippet = first_item.get("snippet", {})
        content_details = first_item.get("contentDetails", {})
        published_at = snippet.get("publishedAt", "unknown")

        return {
            "channel_title": snippet.get("channelTitle", "unknown"),
            "video_title": snippet.get("title", "unknown"),
            "description": snippet.get("description", ""),
            "tags": str(snippet.get("tags", [])),
            "upload_date": published_at[:10] if len(published_at) >= 10 else "unknown",
            "upload_year": published_at[:4] if len(published_at) >= 4 else "unknown",
            "category_id": snippet.get("categoryId", "unknown"),
            "duration_seconds": parse_iso8601_duration_seconds(content_details.get("duration")),
        }
    except Exception as youtube_error:
        report_source_failure(SOURCE_NAME, youtube_error, title=url, artist="unknown")
        return dict(UNKNOWN_DEFAULTS)
