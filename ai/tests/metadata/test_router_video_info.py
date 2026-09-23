from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.auth import INTERNAL_API_KEY_HEADER
from app.config import settings
from app.main import app
from app.metadata.schemas import VideoInfoItem
from app.rate_limit import metadata_resolve_rate_limiter

VIDEO_INFO_ENDPOINT = "/metadata/video-info"
INVALID_INTERNAL_API_KEY = "invalid-internal-key"
UNAUTHORIZED_STATUS_CODE = 401


@pytest.fixture(autouse=True)
def reset_rate_limiter_between_tests():
    metadata_resolve_rate_limiter.reset()
    yield
    metadata_resolve_rate_limiter.reset()


def _post_video_info(client: TestClient, video_ids: list[str]):
    return client.post(
        VIDEO_INFO_ENDPOINT,
        json={"video_ids": video_ids},
        headers={INTERNAL_API_KEY_HEADER: settings.internal_service_api_key},
    )


def test_returns_raw_title_and_channel_per_video():
    items = [VideoInfoItem(video_id="video-one", title="Real Song", channel_title="Real Channel")]
    with patch("app.metadata.router.fetch_video_info", return_value=items):
        with TestClient(app) as client:
            response = _post_video_info(client, ["video-one"])

    assert response.status_code == 200
    assert response.json() == {
        "videos": [{"video_id": "video-one", "title": "Real Song", "channel_title": "Real Channel"}]
    }


def test_omits_ids_the_fetch_could_not_resolve():
    with patch("app.metadata.router.fetch_video_info", return_value=[]):
        with TestClient(app) as client:
            response = _post_video_info(client, ["unknown-video"])

    assert response.status_code == 200
    assert response.json() == {"videos": []}


def test_requires_the_internal_api_key():
    with TestClient(app) as client:
        response = client.post(VIDEO_INFO_ENDPOINT, json={"video_ids": ["video-one"]})

    assert response.status_code in (401, 422)


def test_rejects_an_invalid_internal_api_key():
    with TestClient(app) as client:
        response = client.post(
            VIDEO_INFO_ENDPOINT,
            json={"video_ids": ["video-one"]},
            headers={INTERNAL_API_KEY_HEADER: INVALID_INTERNAL_API_KEY},
        )

    assert response.status_code == UNAUTHORIZED_STATUS_CODE
