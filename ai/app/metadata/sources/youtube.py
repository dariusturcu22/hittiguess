import httpx

from app.config import settings
from app.metadata.sources.util import (
    build_youtube_api_url,
    build_youtube_playlist_items_api_url,
    extract_youtube_video_id,
    parse_iso8601_duration_seconds,
)
from app.observability.error_reporting import report_source_failure

SOURCE_NAME = "youtube"


class PlaylistFetchError(Exception):
    """Raised when the first page of a playlist fetch fails, so a caller can
    distinguish a real API or lookup failure from a playlist that simply has
    no items (which is not an error and yields an empty list instead)."""
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


def fetch_playlist_video_ids(playlist_id: str) -> list[str]:
    """Crawls a YouTube playlist to every video id it contains, paginating
    through playlistItems.list until nextPageToken is absent. A failure on the
    first page raises PlaylistFetchError, since that page failing means the
    playlist could not be read at all; a failure on a later page instead stops
    pagination and returns whatever was collected so far."""
    video_ids: list[str] = []
    page_token: str | None = None
    is_first_page = True

    while True:
        api_url = build_youtube_playlist_items_api_url(playlist_id, settings.youtube_api_key, page_token)
        try:
            response = httpx.get(api_url, timeout=5.0)
            response.raise_for_status()
            page_payload = response.json()
        except Exception as playlist_page_error:
            report_source_failure(SOURCE_NAME, playlist_page_error, title=playlist_id, artist="unknown")
            if is_first_page:
                raise PlaylistFetchError(
                    f"Failed to fetch playlist {playlist_id}"
                ) from playlist_page_error
            break

        for playlist_item in page_payload.get("items", []):
            video_id = playlist_item.get("contentDetails", {}).get("videoId")
            if video_id:
                video_ids.append(video_id)

        page_token = page_payload.get("nextPageToken")
        is_first_page = False
        if not page_token:
            break

    return video_ids
