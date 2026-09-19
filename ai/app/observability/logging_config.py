import json
import logging
import sys
from datetime import datetime, timezone

from opentelemetry import trace
from opentelemetry._logs import set_logger_provider
from opentelemetry.exporter.otlp.proto.http._log_exporter import OTLPLogExporter
from opentelemetry.instrumentation.logging.handler import LoggingHandler
from opentelemetry.sdk._logs import LoggerProvider
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.sdk.resources import SERVICE_NAME, Resource

from app.config import settings
from app.observability.otlp import otlp_headers
from app.observability.request_context import get_current_request_id
from app.observability.tracing import SERVICE_NAME_VALUE

TRACE_ID_HEX_DIGITS = "032x"
SPAN_ID_HEX_DIGITS = "016x"
OTLP_LOGS_PATH = "/v1/logs"


class JsonLineFormatter(logging.Formatter):
    """Renders each log record as a single-line JSON object, the shape a
    Loki/Promtail pipeline expects to scrape, without requiring a live Loki
    endpoint to produce or test it. Includes the current request's correlation
    id and OpenTelemetry trace/span id when either is available, so one user
    action can be traced across this service's and the core service's logs."""

    def format(self, record: logging.LogRecord) -> str:
        log_entry: dict[str, str] = {
            "timestamp": datetime.fromtimestamp(record.created, tz=timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }

        request_id = get_current_request_id()
        if request_id is not None:
            log_entry["requestId"] = request_id

        span_context = trace.get_current_span().get_span_context()
        if span_context.is_valid:
            log_entry["traceId"] = format(span_context.trace_id, TRACE_ID_HEX_DIGITS)
            log_entry["spanId"] = format(span_context.span_id, SPAN_ID_HEX_DIGITS)

        if record.exc_info:
            log_entry["exception"] = self.formatException(record.exc_info)

        return json.dumps(log_entry)


def configure_logging(level: int = logging.INFO) -> None:
    """Every log record always goes to stdout as JSON, independent of whether
    OTLP export is configured. A second handler ships the same records over
    OTLP once otel_exporter_otlp_endpoint points at a real collector or
    Grafana Cloud's OTLP gateway; until then this stays a safe no-op beyond
    the stdout output, the same pattern setup_tracing uses for spans."""
    stdout_handler = logging.StreamHandler(sys.stdout)
    stdout_handler.setFormatter(JsonLineFormatter())

    root_logger = logging.getLogger()
    root_logger.handlers = [stdout_handler]
    root_logger.setLevel(level)

    if settings.otel_exporter_otlp_endpoint:
        resource = Resource.create({SERVICE_NAME: SERVICE_NAME_VALUE})
        logger_provider = LoggerProvider(resource=resource)
        set_logger_provider(logger_provider)

        exporter = OTLPLogExporter(
            endpoint=f"{settings.otel_exporter_otlp_endpoint}{OTLP_LOGS_PATH}",
            headers=otlp_headers(),
        )
        logger_provider.add_log_record_processor(BatchLogRecordProcessor(exporter))

        # Attached to the "app" logger, not the root logger: the exporter's own
        # HTTP calls log through urllib3, which sits outside this namespace.
        # Attaching to root would capture those transport logs too, triggering
        # another export on every export, an unbounded feedback loop.
        otlp_handler = LoggingHandler(level=logging.NOTSET, logger_provider=logger_provider)
        logging.getLogger("app").addHandler(otlp_handler)
