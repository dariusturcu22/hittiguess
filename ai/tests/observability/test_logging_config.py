import json
import logging

from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from app.observability.logging_config import JsonLineFormatter
from app.observability.request_context import _current_request_id


def _make_record(message: str = "something happened") -> logging.LogRecord:
    return logging.LogRecord(
        name="app.metadata.service",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg=message,
        args=(),
        exc_info=None,
    )


def test_formats_a_log_record_as_one_json_line_with_the_core_fields():
    formatted_line = JsonLineFormatter().format(_make_record())
    payload = json.loads(formatted_line)

    assert payload["level"] == "INFO"
    assert payload["logger"] == "app.metadata.service"
    assert payload["message"] == "something happened"
    assert "requestId" not in payload


def test_includes_the_request_id_when_one_is_set_on_the_current_context():
    token = _current_request_id.set("test-request-id")
    try:
        payload = json.loads(JsonLineFormatter().format(_make_record()))
    finally:
        _current_request_id.reset(token)

    assert payload["requestId"] == "test-request-id"


def test_includes_the_trace_and_span_id_when_a_span_is_active():
    exporter = InMemorySpanExporter()
    tracer_provider = TracerProvider()
    tracer_provider.add_span_processor(SimpleSpanProcessor(exporter))
    tracer = tracer_provider.get_tracer("test")

    with tracer.start_as_current_span("test-span") as span:
        expected_trace_id = format(span.get_span_context().trace_id, "032x")
        expected_span_id = format(span.get_span_context().span_id, "016x")
        payload = json.loads(JsonLineFormatter().format(_make_record()))

    assert payload["traceId"] == expected_trace_id
    assert payload["spanId"] == expected_span_id
