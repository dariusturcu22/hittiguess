import json
import logging

import pytest
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from app.observability.logging_config import (
    REDACTED_VALUE,
    JsonLineFormatter,
    SecretRedactionFilter,
    configure_logging,
    redact_secrets,
)
from app.observability.request_context import _current_request_id


@pytest.fixture
def restore_logging_state():
    root_logger = logging.getLogger()
    app_logger = logging.getLogger("app")
    original_root_handlers = list(root_logger.handlers)
    original_app_handlers = list(app_logger.handlers)
    try:
        yield
    finally:
        root_logger.handlers = original_root_handlers
        app_logger.handlers = original_app_handlers


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


def test_configure_logging_skips_the_otlp_handler_with_no_endpoint_configured(
    mocker, restore_logging_state
):
    # Other test modules import the real app.main at collection time, which
    # calls the real configure_logging(); comparing against a snapshot of
    # "app"'s own handlers, not an assumed-empty list, keeps this test correct
    # regardless of that unrelated, already-happened side effect.
    app_handlers_before = list(logging.getLogger("app").handlers)
    mocker.patch("app.observability.logging_config.settings").otel_exporter_otlp_endpoint = None

    configure_logging()

    assert logging.getLogger("app").handlers == app_handlers_before


def test_configure_logging_attaches_the_otlp_handler_to_app_not_root(
    mocker, restore_logging_state
):
    """Attaching to the root logger would also capture the OTLP exporter's own
    HTTP transport logs (urllib3 and friends), which get exported too,
    triggering another export on every export: an unbounded feedback loop.
    The "app" logger is the parent of every logging.getLogger(__name__) call
    in this codebase but sits outside that transport-library namespace."""
    settings_mock = mocker.patch("app.observability.logging_config.settings")
    settings_mock.otel_exporter_otlp_endpoint = "https://otlp-gateway.example.com/otlp"
    mocker.patch(
        "app.observability.logging_config.otlp_headers",
        return_value={"authorization": "Basic abc123"},
    )
    exporter_mock = mocker.patch("app.observability.logging_config.OTLPLogExporter")

    app_handlers_before = list(logging.getLogger("app").handlers)

    configure_logging()

    exporter_mock.assert_called_once_with(
        endpoint="https://otlp-gateway.example.com/otlp/v1/logs",
        headers={"authorization": "Basic abc123"},
    )
    app_handlers_added = [
        handler for handler in logging.getLogger("app").handlers if handler not in app_handlers_before
    ]
    assert len(app_handlers_added) == 1
    # configure_logging() always replaces root's handlers with just the stdout
    # one; the OTLP handler belongs on "app" only, never on root.
    assert len(logging.getLogger().handlers) == 1


FAKE_API_KEY = "fake-key-value-for-tests"
YOUTUBE_URL_TEMPLATE = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id=abc123&key={key}"


def test_redacts_a_credential_query_parameter_and_keeps_the_rest_of_the_url():
    redacted = redact_secrets(YOUTUBE_URL_TEMPLATE.format(key=FAKE_API_KEY))

    assert FAKE_API_KEY not in redacted
    assert redacted == YOUTUBE_URL_TEMPLATE.format(key=REDACTED_VALUE)


@pytest.mark.parametrize("parameter_name", ["key", "api_key", "apikey", "token", "access_token", "client_secret", "password"])
def test_redacts_every_known_credential_parameter_name(parameter_name):
    url = f"https://api.example.test/search?q=hello&{parameter_name}={FAKE_API_KEY}&limit=5"

    redacted = redact_secrets(url)

    assert FAKE_API_KEY not in redacted
    assert "q=hello" in redacted and "limit=5" in redacted


def test_leaves_urls_without_credentials_and_lookalike_parameters_alone():
    url = "https://musicbrainz.org/ws/2/release-group/?query=title&monkey=1&limit=10"

    assert redact_secrets(url) == url


def test_the_json_formatter_redacts_the_message_and_the_exception_text():
    try:
        raise RuntimeError(f"Client error for url {YOUTUBE_URL_TEMPLATE.format(key=FAKE_API_KEY)}")
    except RuntimeError:
        import sys
        record = _make_record(f"HTTP Request: GET {YOUTUBE_URL_TEMPLATE.format(key=FAKE_API_KEY)}")
        record.exc_info = sys.exc_info()

    formatted_line = JsonLineFormatter().format(record)

    assert FAKE_API_KEY not in formatted_line
    assert REDACTED_VALUE in json.loads(formatted_line)["message"]


def test_the_redaction_filter_cleans_a_record_that_uses_format_arguments():
    record = _make_record("HTTP Request: GET %s")
    record.args = (YOUTUBE_URL_TEMPLATE.format(key=FAKE_API_KEY),)

    SecretRedactionFilter().filter(record)

    assert FAKE_API_KEY not in record.getMessage()
    assert record.args == ()
