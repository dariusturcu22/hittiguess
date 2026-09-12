from concurrent.futures import ThreadPoolExecutor
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.auth import INTERNAL_API_KEY_HEADER
from app.config import settings
from app.main import app
from app.metadata.schemas import MetadataResolveResponse, SongMetadataResult
from app.rate_limit import METADATA_RESOLVE_MAX_REQUESTS_PER_WINDOW, metadata_resolve_rate_limiter

CONCURRENT_RESOLVE_REQUESTS = METADATA_RESOLVE_MAX_REQUESTS_PER_WINDOW * 2
TOO_MANY_REQUESTS_STATUS_CODE = 429


def _fake_resolve_response():
    return MetadataResolveResponse(
        status="SUCCESS",
        model=settings.openai_model,
        content=SongMetadataResult(
            title="Test Song",
            artist="Test Artist",
            release_year=1999,
            gradient_color1="8B5CF6",
            gradient_color2="EC4899",
            confidence="high",
            source="MusicBrainz",
            reasoning="Matched exactly.",
        ),
    )


@pytest.fixture(autouse=True)
def reset_rate_limiter_between_tests():
    metadata_resolve_rate_limiter.reset()
    yield
    metadata_resolve_rate_limiter.reset()


def _post_resolve(client: TestClient) -> int:
    response = client.post(
        "/metadata/resolve",
        json={"youtube_url": "https://youtube.com/watch?v=test"},
        headers={INTERNAL_API_KEY_HEADER: settings.internal_service_api_key},
    )
    return response.status_code


def test_requests_up_to_the_limit_succeed():
    with patch("app.metadata.router.resolve_metadata", return_value=_fake_resolve_response()):
        with TestClient(app) as client:
            for _ in range(METADATA_RESOLVE_MAX_REQUESTS_PER_WINDOW):
                assert _post_resolve(client) == 200


def test_the_request_over_the_limit_is_rejected_independent_of_the_internal_api_key():
    with patch("app.metadata.router.resolve_metadata", return_value=_fake_resolve_response()):
        with TestClient(app) as client:
            for _ in range(METADATA_RESOLVE_MAX_REQUESTS_PER_WINDOW):
                _post_resolve(client)

            assert _post_resolve(client) == TOO_MANY_REQUESTS_STATUS_CODE


def test_the_rate_limit_holds_under_real_concurrent_traffic():
    with patch("app.metadata.router.resolve_metadata", return_value=_fake_resolve_response()):
        with TestClient(app) as client:
            with ThreadPoolExecutor(max_workers=CONCURRENT_RESOLVE_REQUESTS) as executor:
                statuses = list(executor.map(lambda _: _post_resolve(client), range(CONCURRENT_RESOLVE_REQUESTS)))

    successful_count = statuses.count(200)
    rate_limited_count = statuses.count(TOO_MANY_REQUESTS_STATUS_CODE)

    assert successful_count == METADATA_RESOLVE_MAX_REQUESTS_PER_WINDOW
    assert rate_limited_count == CONCURRENT_RESOLVE_REQUESTS - METADATA_RESOLVE_MAX_REQUESTS_PER_WINDOW
