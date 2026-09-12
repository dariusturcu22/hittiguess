from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.observability.request_context import (
    REQUEST_ID_HEADER,
    CorrelationIdMiddleware,
    get_current_request_id,
)


def _build_app() -> FastAPI:
    app = FastAPI()
    app.add_middleware(CorrelationIdMiddleware)

    @app.get("/probe")
    def probe() -> dict[str, str | None]:
        return {"request_id_seen_by_handler": get_current_request_id()}

    return app


def test_generates_a_request_id_when_none_is_supplied():
    client = TestClient(_build_app())

    response = client.get("/probe")

    assert response.status_code == 200
    generated_request_id = response.headers[REQUEST_ID_HEADER]
    assert generated_request_id
    assert response.json()["request_id_seen_by_handler"] == generated_request_id


def test_reuses_the_incoming_request_id():
    client = TestClient(_build_app())

    response = client.get("/probe", headers={REQUEST_ID_HEADER: "caller-supplied-id"})

    assert response.headers[REQUEST_ID_HEADER] == "caller-supplied-id"
    assert response.json()["request_id_seen_by_handler"] == "caller-supplied-id"


def test_request_id_is_not_visible_outside_the_request_that_set_it():
    assert get_current_request_id() is None
