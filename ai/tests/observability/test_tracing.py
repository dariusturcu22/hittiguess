from fastapi import FastAPI
from opentelemetry import trace

from app.observability.tracing import setup_tracing


def test_setup_tracing_installs_a_real_tracer_provider_with_no_endpoint_configured(mocker):
    mocker.patch("app.observability.tracing.settings").otel_exporter_otlp_endpoint = None

    app = FastAPI()

    @app.get("/probe")
    def probe():
        return {"ok": True}

    setup_tracing(app)

    tracer = trace.get_tracer("test-no-endpoint")
    with tracer.start_as_current_span("test-span") as span:
        assert span.get_span_context().is_valid


def test_setup_tracing_adds_an_otlp_exporter_when_an_endpoint_is_configured(mocker):
    settings_mock = mocker.patch("app.observability.tracing.settings")
    settings_mock.otel_exporter_otlp_endpoint = "https://otlp-gateway.example.com/otlp"
    mocker.patch(
        "app.observability.tracing.otlp_headers", return_value={"authorization": "Basic abc123"}
    )
    add_processor_mock = mocker.patch(
        "app.observability.tracing.TracerProvider.add_span_processor"
    )
    exporter_mock = mocker.patch("app.observability.tracing.OTLPSpanExporter")

    app = FastAPI()
    setup_tracing(app)

    add_processor_mock.assert_called_once()
    # The signal path must be appended explicitly: passing endpoint directly
    # to the exporter constructor, rather than letting the SDK read it from
    # the environment, skips the SDK's own per-signal path resolution, so a
    # bare base URL here would silently 404 against a real OTLP gateway.
    exporter_mock.assert_called_once_with(
        endpoint="https://otlp-gateway.example.com/otlp/v1/traces",
        headers={"authorization": "Basic abc123"},
    )
