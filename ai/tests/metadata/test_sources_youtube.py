import httpx
import pytest
import respx
from httpx import Response

from app.metadata.sources import youtube

VIDEO_API_HOST = "https://www.googleapis.com/youtube/v3/videos"
PLAYLIST_ITEMS_API_HOST = "https://www.googleapis.com/youtube/v3/playlistItems"


def _api_response_payload():
    return {
        "items": [
            {
                "snippet": {
                    "channelTitle": "Real Artist",
                    "title": "Real Song",
                    "description": "A song description.",
                    "tags": ["music"],
                    "publishedAt": "2014-05-01T00:00:00Z",
                    "categoryId": "10",
                },
                "contentDetails": {"duration": "PT3M52S"},
            }
        ]
    }


@respx.mock
def test_fetch_youtube_metadata_extracts_category_and_duration():
    respx.get(url__startswith=VIDEO_API_HOST).mock(return_value=Response(200, json=_api_response_payload()))

    data = youtube.fetch_youtube_metadata("https://youtube.com/watch?v=dQw4w9WgXcQ")

    assert data["category_id"] == "10"
    assert data["duration_seconds"] == 232
    assert data["video_title"] == "Real Song"


@respx.mock
def test_fetch_youtube_metadata_missing_content_details_yields_none_duration():
    payload = _api_response_payload()
    del payload["items"][0]["contentDetails"]
    del payload["items"][0]["snippet"]["categoryId"]
    respx.get(url__startswith=VIDEO_API_HOST).mock(return_value=Response(200, json=payload))

    data = youtube.fetch_youtube_metadata("https://youtube.com/watch?v=dQw4w9WgXcQ")

    assert data["category_id"] == "unknown"
    assert data["duration_seconds"] is None


@respx.mock
def test_fetch_youtube_metadata_no_items_returns_unknown_defaults():
    respx.get(url__startswith=VIDEO_API_HOST).mock(return_value=Response(200, json={"items": []}))

    data = youtube.fetch_youtube_metadata("https://youtube.com/watch?v=dQw4w9WgXcQ")

    assert data == youtube.UNKNOWN_DEFAULTS
    assert data["category_id"] == "unknown"
    assert data["duration_seconds"] is None


def _playlist_page_payload(video_ids: list[str], next_page_token: str | None = None) -> dict:
    payload = {"items": [{"contentDetails": {"videoId": video_id}} for video_id in video_ids]}
    if next_page_token:
        payload["nextPageToken"] = next_page_token
    return payload


@respx.mock
def test_fetch_playlist_video_ids_paginates_across_multiple_pages():
    pages_by_token = {
        None: _playlist_page_payload(["video-one", "video-two"], "page-two-token"),
        "page-two-token": _playlist_page_payload(["video-three", "video-four"]),
    }

    def respond_for_page(request: httpx.Request) -> Response:
        page_token = request.url.params.get("pageToken")
        return Response(200, json=pages_by_token[page_token])

    respx.get(url__startswith=PLAYLIST_ITEMS_API_HOST).mock(side_effect=respond_for_page)

    video_ids = youtube.fetch_playlist_video_ids("PL123")

    assert video_ids == ["video-one", "video-two", "video-three", "video-four"]


@respx.mock
def test_fetch_playlist_video_ids_with_zero_items_returns_an_empty_list():
    respx.get(url__startswith=PLAYLIST_ITEMS_API_HOST).mock(return_value=Response(200, json={"items": []}))

    assert youtube.fetch_playlist_video_ids("PL123") == []


@respx.mock
def test_fetch_playlist_video_ids_raises_when_the_first_page_fails():
    respx.get(url__startswith=PLAYLIST_ITEMS_API_HOST).mock(return_value=Response(404, json={"error": "not found"}))

    with pytest.raises(youtube.PlaylistFetchError):
        youtube.fetch_playlist_video_ids("does-not-exist")


@respx.mock
def test_fetch_playlist_video_ids_stops_and_returns_partial_results_when_a_later_page_fails():
    call_count = {"total": 0}

    def respond_first_page_then_fail(request: httpx.Request) -> Response:
        call_count["total"] += 1
        if call_count["total"] == 1:
            return Response(200, json=_playlist_page_payload(["video-one"], "page-two-token"))
        raise httpx.ConnectError("connection reset")

    respx.get(url__startswith=PLAYLIST_ITEMS_API_HOST).mock(side_effect=respond_first_page_then_fail)

    video_ids = youtube.fetch_playlist_video_ids("PL123")

    assert video_ids == ["video-one"]
    assert call_count["total"] == 2
