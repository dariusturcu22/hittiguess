from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.auth import INTERNAL_API_KEY_HEADER
from app.config import settings
from app.main import app
from app.metadata.service import InvalidPlaylistLinkError
from app.metadata.sources.youtube import PlaylistFetchError
from app.rate_limit import metadata_resolve_rate_limiter

PLAYLIST_VIDEO_IDS_ENDPOINT = "/metadata/playlist-video-ids"


@pytest.fixture(autouse=True)
def reset_rate_limiter_between_tests():
    metadata_resolve_rate_limiter.reset()
    yield
    metadata_resolve_rate_limiter.reset()


def _post_playlist_video_ids(client: TestClient, playlist_url_or_id: str):
    return client.post(
        PLAYLIST_VIDEO_IDS_ENDPOINT,
        json={"playlist_url_or_id": playlist_url_or_id},
        headers={INTERNAL_API_KEY_HEADER: settings.internal_service_api_key},
    )


def test_expands_a_playlist_link_into_its_video_ids():
    with patch("app.metadata.router.expand_playlist", return_value=["video-one", "video-two"]):
        with TestClient(app) as client:
            response = _post_playlist_video_ids(client, "https://youtube.com/playlist?list=PL123")

    assert response.status_code == 200
    assert response.json() == {"video_ids": ["video-one", "video-two"]}


def test_an_invalid_playlist_link_returns_a_client_error():
    with patch("app.metadata.router.expand_playlist", side_effect=InvalidPlaylistLinkError("not a playlist")):
        with TestClient(app) as client:
            response = _post_playlist_video_ids(client, "not a playlist link")

    assert response.status_code == 400


def test_an_upstream_fetch_failure_returns_a_gateway_error():
    with patch("app.metadata.router.expand_playlist", side_effect=PlaylistFetchError("playlist API failed")):
        with TestClient(app) as client:
            response = _post_playlist_video_ids(client, "https://youtube.com/playlist?list=PL123")

    assert response.status_code == 502


def test_requires_the_internal_api_key():
    with TestClient(app) as client:
        response = client.post(
            PLAYLIST_VIDEO_IDS_ENDPOINT, json={"playlist_url_or_id": "https://youtube.com/playlist?list=PL123"}
        )

    assert response.status_code in (401, 422)
