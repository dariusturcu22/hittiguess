import respx
from httpx import Response

from app.metadata.sources import youtube

VIDEO_API_HOST = "https://www.googleapis.com/youtube/v3/videos"


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
