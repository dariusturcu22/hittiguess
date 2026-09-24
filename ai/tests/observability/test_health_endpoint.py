from fastapi.testclient import TestClient

from app.auth import INTERNAL_API_KEY_HEADER
from app.config import settings
from app.main import app
from app.main import require_internal_api_key_configured
import pytest

METRICS_PATH = "/metrics"
WRONG_INTERNAL_API_KEY = "not-the-internal-key"
OK_STATUS_CODE = 200
UNAUTHORIZED_STATUS_CODE = 401
# FastAPI answers a request missing a required header with a validation error.
MISSING_HEADER_STATUS_CODE = 422


def test_health_endpoint_reports_ok():
    client = TestClient(app)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_metrics_endpoint_serves_a_caller_with_the_internal_key():
    client = TestClient(app)

    response = client.get(METRICS_PATH, headers={INTERNAL_API_KEY_HEADER: settings.internal_service_api_key})

    assert response.status_code == OK_STATUS_CODE
    assert "text/plain" in response.headers["content-type"]


def test_metrics_endpoint_refuses_a_wrong_internal_key():
    client = TestClient(app)

    response = client.get(METRICS_PATH, headers={INTERNAL_API_KEY_HEADER: WRONG_INTERNAL_API_KEY})

    assert response.status_code == UNAUTHORIZED_STATUS_CODE


def test_metrics_endpoint_refuses_a_caller_without_the_internal_key():
    client = TestClient(app)

    response = client.get(METRICS_PATH)

    assert response.status_code == MISSING_HEADER_STATUS_CODE


def test_empty_internal_api_key_refuses_startup():
    with pytest.raises(RuntimeError, match="INTERNAL_SERVICE_API_KEY"):
        require_internal_api_key_configured("")
