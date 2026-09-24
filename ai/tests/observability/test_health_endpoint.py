from fastapi.testclient import TestClient

from app.main import app


def test_health_endpoint_reports_ok():
    client = TestClient(app)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_metrics_endpoint_is_disabled():
    client = TestClient(app)

    response = client.get("/metrics")

    assert response.status_code == 404
