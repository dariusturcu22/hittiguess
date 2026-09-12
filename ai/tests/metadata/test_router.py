from fastapi.testclient import TestClient

from app.config import settings
from app.main import app
from app.metadata import router as router_module
from app.metadata.schemas import MetadataResolveResponse, SongMetadataResult

client = TestClient(app)

INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key"


def _sample_response():
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


def test_resolve_rejects_a_request_with_no_internal_api_key_header():
    response = client.post("/metadata/resolve", json={"youtube_url": "https://youtube.com/watch?v=abc12345678"})

    assert response.status_code == 422


def test_resolve_rejects_a_request_with_a_wrong_internal_api_key(mocker):
    resolve_mock = mocker.patch.object(router_module, "resolve_metadata")

    response = client.post(
        "/metadata/resolve",
        json={"youtube_url": "https://youtube.com/watch?v=abc12345678"},
        headers={INTERNAL_API_KEY_HEADER: "not-the-real-key"},
    )

    assert response.status_code == 401
    resolve_mock.assert_not_called()


def test_resolve_delegates_to_the_service_and_returns_its_result(mocker):
    resolve_mock = mocker.patch.object(router_module, "resolve_metadata", return_value=_sample_response())

    response = client.post(
        "/metadata/resolve",
        json={"youtube_url": "https://youtube.com/watch?v=abc12345678"},
        headers={INTERNAL_API_KEY_HEADER: settings.internal_service_api_key},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCESS"
    assert body["content"]["title"] == "Test Song"
    resolve_mock.assert_called_once_with("https://youtube.com/watch?v=abc12345678")
