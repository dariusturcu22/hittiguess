import httpx

from app.config import settings
from app.metadata.sources.util import (
    YOUTUBE_VIDEOS_BATCH_SIZE,
    build_youtube_api_url,
    build_youtube_playlist_items_api_url,
    build_youtube_videos_batch_api_url,
    extract_youtube_video_id,
    parse_iso8601_duration_seconds,
)
from app.observability.error_reporting import report_source_failure
from app.usage import recorder as usage_recorder
from app.usage.recorder import OPERATION_YOUTUBE_PLAYLIST_ITEMS_LIST, OPERATION_YOUTUBE_VIDEOS_LIST

SOURCE_NAME = "youtube"
VIDEO_DURATIONS_ENDPOINT = "https://www.googleapis.com/youtube/v3/videos"
VIDEO_DURATIONS_PART = "contentDetails"
VIDEO_LOOKUP_TIMEOUT_SECONDS = 5.0


class PlaylistFetchError(Exception):
    """Raised when the first page of a playlist fetch fails, so a caller can
    distinguish a real API or lookup failure from a playlist that simply has
    no items (which is not an error and yields an empty list instead)."""


def _youtube_get(operation: str, url: str, **request_options) -> httpx.Response:
    """Sends one YouTube Data API request and counts its quota once a response
    arrives, whatever the status, since YouTube charges failed requests too."""
    response = httpx.get(url, **request_options)
    usage_recorder.record_youtube_request(operation)
    return response


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


class VideoUnavailableError(Exception):
    """Raised when YouTube has no video for the requested id."""


def _fetch_video_metadata(video_id: str | None) -> dict[str, str] | None:
    """One videos.list lookup. None when YouTube returns no item for the id; a
    failed request raises."""
    api_url = build_youtube_api_url(video_id, settings.youtube_api_key)

    response = _youtube_get(OPERATION_YOUTUBE_VIDEOS_LIST, api_url, timeout=5.0)
    response.raise_for_status()
    items = response.json().get("items", [])

    if not items:
        return None

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


def fetch_youtube_metadata(url: str) -> dict[str, str]:
    try:
        video_metadata = _fetch_video_metadata(extract_youtube_video_id(url))
        return video_metadata if video_metadata is not None else dict(UNKNOWN_DEFAULTS)
    except Exception as youtube_error:
        report_source_failure(SOURCE_NAME, youtube_error, title=url, artist="unknown")
        return dict(UNKNOWN_DEFAULTS)


def fetch_youtube_metadata_or_raise(youtube_id: str) -> dict[str, str]:
    """The same lookup as fetch_youtube_metadata for a video id or link, raising
    instead of falling back to unknown defaults: VideoUnavailableError when the id
    does not parse or YouTube has no such video, the request error otherwise."""
    video_id = extract_youtube_video_id(youtube_id)
    if video_id is None:
        raise VideoUnavailableError("Not a valid YouTube video id")
    video_metadata = _fetch_video_metadata(video_id)
    if video_metadata is None:
        raise VideoUnavailableError("YouTube returned no video for the id")
    return video_metadata


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
            response = _youtube_get(OPERATION_YOUTUBE_PLAYLIST_ITEMS_LIST, api_url, timeout=5.0)
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


def fetch_video_titles_and_channels(video_ids: list[str]) -> dict[str, dict[str, str]]:
    """Batch-fetches each video's raw title and uploading channel name via
    videos.list, chunked to the API's 50-id-per-call limit. Best effort: a
    failed chunk is skipped rather than raising, since this only feeds a
    still-processing row's display text, not the resolution pipeline itself.
    A video id absent from the result (a failed chunk, or an id YouTube
    doesn't recognize) is left for the caller to default."""
    video_info_by_id: dict[str, dict[str, str]] = {}

    for chunk_start in range(0, len(video_ids), YOUTUBE_VIDEOS_BATCH_SIZE):
        chunk = video_ids[chunk_start:chunk_start + YOUTUBE_VIDEOS_BATCH_SIZE]
        api_url = build_youtube_videos_batch_api_url(chunk, settings.youtube_api_key)
        try:
            response = _youtube_get(OPERATION_YOUTUBE_VIDEOS_LIST, api_url, timeout=5.0)
            response.raise_for_status()
            payload = response.json()
        except Exception as video_batch_error:
            report_source_failure(SOURCE_NAME, video_batch_error, title=",".join(chunk), artist="unknown")
            continue

        for item in payload.get("items", []):
            video_id = item.get("id")
            if not video_id:
                continue
            snippet = item.get("snippet", {})
            video_info_by_id[video_id] = {
                "title": snippet.get("title", "unknown"),
                "channel_title": snippet.get("channelTitle", "unknown"),
            }

    return video_info_by_id


def fetch_video_durations(video_ids: list[str]) -> dict[str, int | None]:
    unique_video_ids = list(dict.fromkeys(video_ids))
    response = _youtube_get(
        OPERATION_YOUTUBE_VIDEOS_LIST,
        VIDEO_DURATIONS_ENDPOINT,
        params={"part": VIDEO_DURATIONS_PART, "id": ",".join(unique_video_ids), "key": settings.youtube_api_key},
        timeout=VIDEO_LOOKUP_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    durations = dict.fromkeys(unique_video_ids)
    for video in response.json()["items"]:
        video_id = video.get("id")
        if video_id in durations:
            durations[video_id] = parse_iso8601_duration_seconds(video.get("contentDetails", {}).get("duration"))
    return durations
