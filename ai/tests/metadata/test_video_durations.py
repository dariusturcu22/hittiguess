import httpx
import pytest
import respx
from fastapi.testclient import TestClient

from app.auth import INTERNAL_API_KEY_HEADER
from app.config import settings
from app.main import app
from app.metadata.sources.youtube import fetch_video_durations
from app.metadata.sources.util import YOUTUBE_VIDEOS_BATCH_SIZE
from app.rate_limit import fast_tier_rate_limiter

ENDPOINT = "/metadata/video-durations"
VIDEO_API = "https://www.googleapis.com/youtube/v3/videos"
SUCCESS = 200
UNAUTHORIZED = 401
INVALID_REQUEST = 422
UPSTREAM_FAILURE = 502
QUOTA_EXHAUSTED = 403
OFFICIAL_DURATION_SECONDS = 210
OVERSIZED_BATCH_COUNT = YOUTUBE_VIDEOS_BATCH_SIZE + 1
HEADERS = {INTERNAL_API_KEY_HEADER: settings.internal_service_api_key}


@pytest.fixture(autouse=True)
def reset_limiter():
    fast_tier_rate_limiter.reset()
    yield
    fast_tier_rate_limiter.reset()


@respx.mock
def test_batches_known_ids_once_and_marks_missing_videos_unavailable():
    lookup = respx.get(VIDEO_API).mock(return_value=httpx.Response(SUCCESS, json={
        "items": [{"id": "known", "contentDetails": {"duration": "PT3M30S"}}],
    }))
    with TestClient(app) as client:
        response = client.post(ENDPOINT, json={"video_ids": ["known", "deleted", "known"]}, headers=HEADERS)
    assert response.status_code == SUCCESS
    assert response.json() == {"durations": {"known": OFFICIAL_DURATION_SECONDS, "deleted": None}}
    assert lookup.call_count == 1
    request, = [call.request for call in lookup.calls]
    assert request.url.params["id"] == "known,deleted"
    assert request.url.params["part"] == "contentDetails"


@respx.mock
def test_quota_failure_is_not_reported_as_a_successful_missing_video():
    respx.get(VIDEO_API).mock(return_value=httpx.Response(QUOTA_EXHAUSTED))
    with TestClient(app) as client:
        response = client.post(ENDPOINT, json={"video_ids": ["known"]}, headers=HEADERS)
    assert response.status_code == UPSTREAM_FAILURE


def test_requires_internal_authentication():
    with TestClient(app) as client:
        response = client.post(ENDPOINT, json={"video_ids": ["known"]}, headers={INTERNAL_API_KEY_HEADER: "invalid"})
    assert response.status_code == UNAUTHORIZED


@pytest.mark.parametrize("video_ids", [[], ["video"] * OVERSIZED_BATCH_COUNT])
def test_rejects_empty_or_oversized_batches(video_ids):
    with TestClient(app) as client:
        response = client.post(ENDPOINT, json={"video_ids": video_ids}, headers=HEADERS)
    assert response.status_code == INVALID_REQUEST


@respx.mock
def test_source_propagates_network_failure_for_retry():
    respx.get(VIDEO_API).mock(side_effect=httpx.ConnectError("unavailable"))
    with pytest.raises(httpx.ConnectError):
        fetch_video_durations(["known"])
