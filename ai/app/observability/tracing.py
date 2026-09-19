from fastapi import FastAPI
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.sdk.resources import SERVICE_NAME, Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

from app.config import settings
from app.observability.otlp import otlp_headers

SERVICE_NAME_VALUE = "ai-service"
OTLP_TRACES_PATH = "/v1/traces"


def setup_tracing(app: FastAPI) -> None:
    """Auto-instruments FastAPI for distributed tracing. A real SDK tracer
    provider is always installed, so every request gets a real trace/span id
    for log correlation even with no collector configured. Spans are only
    exported over OTLP once otel_exporter_otlp_endpoint points at a real
    collector or Grafana Cloud's OTLP gateway; until then this stays a safe
    no-op beyond the in-process span creation. The signal path is appended
    explicitly because passing endpoint directly to the exporter, rather than
    letting the SDK read it from the process environment, skips the SDK's own
    per-signal path resolution."""
    resource = Resource.create({SERVICE_NAME: SERVICE_NAME_VALUE})
    tracer_provider = TracerProvider(resource=resource)

    if settings.otel_exporter_otlp_endpoint:
        exporter = OTLPSpanExporter(
            endpoint=f"{settings.otel_exporter_otlp_endpoint}{OTLP_TRACES_PATH}",
            headers=otlp_headers(),
        )
        tracer_provider.add_span_processor(BatchSpanProcessor(exporter))

    trace.set_tracer_provider(tracer_provider)
    FastAPIInstrumentor.instrument_app(app)
