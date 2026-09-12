import json
import logging
import sys
from datetime import datetime, timezone

from opentelemetry import trace

from app.observability.request_context import get_current_request_id

TRACE_ID_HEX_DIGITS = "032x"
SPAN_ID_HEX_DIGITS = "016x"


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
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonLineFormatter())

    root_logger = logging.getLogger()
    root_logger.handlers = [handler]
    root_logger.setLevel(level)
