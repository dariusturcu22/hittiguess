from fastapi.testclient import TestClient

from app.main import app
from app.dedup import router
from app.dedup.indexing import IndexingResult

ENDPOINT = "/catalog/index-next"
INTERNAL_HEADERS = {"X-Internal-Api-Key": "test-internal-key"}
HTTP_OK = 200
HTTP_UNAUTHORIZED = 401


def test_indexing_endpoint_requires_internal_key(mocker):
    index = mocker.patch.object(router, "index_next_song")
    response = TestClient(app).post(ENDPOINT, headers={"X-Internal-Api-Key": "wrong-key"})
    assert response.status_code == HTTP_UNAUTHORIZED
    index.assert_not_called()


def test_internal_indexing_endpoint_returns_structured_result(mocker):
    index = mocker.patch.object(router, "index_next_song", return_value=IndexingResult(indexed=True))
    response = TestClient(app).post(ENDPOINT, headers=INTERNAL_HEADERS)
    assert response.status_code == HTTP_OK
    assert response.json() == {"indexed": True, "retry_scheduled": False}
    index.assert_called_once_with()
