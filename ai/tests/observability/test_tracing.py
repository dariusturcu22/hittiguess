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
    mocker.patch("app.observability.tracing.settings").otel_exporter_otlp_endpoint = "http://localhost:4318/v1/traces"
    add_processor_mock = mocker.patch(
        "app.observability.tracing.TracerProvider.add_span_processor"
    )
    mocker.patch("app.observability.tracing.OTLPSpanExporter")

    app = FastAPI()
    setup_tracing(app)

    add_processor_mock.assert_called_once()
