from fastapi.testclient import TestClient

from app.main import app
from app.main import require_internal_api_key_configured
import pytest


def test_health_endpoint_reports_ok():
    client = TestClient(app)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_metrics_endpoint_is_disabled():
    client = TestClient(app)

    response = client.get("/metrics")

    assert response.status_code == 404


def test_empty_internal_api_key_refuses_startup():
    with pytest.raises(RuntimeError, match="INTERNAL_SERVICE_API_KEY"):
        require_internal_api_key_configured("")
