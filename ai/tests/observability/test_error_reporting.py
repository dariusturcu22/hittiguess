import logging

import httpx
import openai
import pytest

from app.observability import error_reporting
from app.observability.logging_config import JsonLineFormatter

SECRET_KEY_VALUE = "secret-key-value"
PROVIDER_URL = f"https://api.example.com/v1/search?key={SECRET_KEY_VALUE}"
HTTP_UNAUTHORIZED = 401
HTTP_NOT_FOUND = 404
HTTP_TOO_MANY_REQUESTS = 429
HTTP_BAD_GATEWAY = 502
HTTP_SERVICE_UNAVAILABLE = 503
SOURCE_NAME = "YOUTUBE_METADATA"


def _request():
    return httpx.Request("GET", PROVIDER_URL)


def _httpx_status_error(status_code):
    return httpx.HTTPStatusError(
        f"Client error for url {PROVIDER_URL}", request=_request(), response=httpx.Response(status_code, request=_request())
    )


def _openai_status_error(status_code):
    return openai.APIStatusError(
        "Model busy, retry later", response=httpx.Response(status_code, request=_request()), body=None
    )


TRANSIENT_ERRORS = [
    _openai_status_error(HTTP_TOO_MANY_REQUESTS),
    _openai_status_error(HTTP_SERVICE_UNAVAILABLE),
    _httpx_status_error(HTTP_TOO_MANY_REQUESTS),
    _httpx_status_error(HTTP_BAD_GATEWAY),
    httpx.ReadTimeout("timed out", request=_request()),
    httpx.ConnectError("refused", request=_request()),
    openai.APITimeoutError(request=_request()),
    openai.APIConnectionError(request=_request()),
]

UNEXPECTED_ERRORS = [
    _openai_status_error(HTTP_UNAUTHORIZED),
    _httpx_status_error(HTTP_NOT_FOUND),
    RuntimeError("musicbrainz is down"),
    ValueError("response did not match the expected schema"),
]


@pytest.mark.parametrize("error", TRANSIENT_ERRORS)
def test_a_transient_upstream_failure_is_a_warning_without_trace_or_error_report(mocker, caplog, error):
    capture_mock = mocker.patch.object(error_reporting.sentry_sdk, "capture_exception")

    with caplog.at_level(logging.INFO):
        error_reporting.report_source_failure(SOURCE_NAME, error, title="Song", artist="Artist")

    (record,) = caplog.records
    assert record.levelno == logging.WARNING
    assert record.exc_info is None
    assert SOURCE_NAME in record.getMessage()
    assert type(error).__name__ in record.getMessage()
    capture_mock.assert_not_called()


@pytest.mark.parametrize("error", UNEXPECTED_ERRORS)
def test_an_unexpected_failure_stays_an_error_with_its_trace_and_report(mocker, caplog, error):
    capture_mock = mocker.patch.object(error_reporting.sentry_sdk, "capture_exception")

    with caplog.at_level(logging.INFO):
        error_reporting.report_source_failure(SOURCE_NAME, error, title="Song", artist="Artist")

    (record,) = caplog.records
    assert record.levelno == logging.ERROR
    assert record.exc_info is not None
    capture_mock.assert_called_once_with(error)


def test_a_rate_limit_warning_names_the_upstream_status_and_never_the_request_url(mocker, caplog):
    mocker.patch.object(error_reporting.sentry_sdk, "capture_exception")

    with caplog.at_level(logging.INFO):
        error_reporting.report_source_failure(
            SOURCE_NAME, _httpx_status_error(HTTP_TOO_MANY_REQUESTS), title="Song", artist="Artist"
        )

    (record,) = caplog.records
    formatted = JsonLineFormatter().format(record)
    assert str(HTTP_TOO_MANY_REQUESTS) in formatted
    assert SECRET_KEY_VALUE not in formatted
    assert PROVIDER_URL not in formatted


def test_an_error_trace_still_has_its_secrets_redacted(mocker, caplog):
    mocker.patch.object(error_reporting.sentry_sdk, "capture_exception")

    with caplog.at_level(logging.INFO):
        error_reporting.report_source_failure(
            SOURCE_NAME, _httpx_status_error(HTTP_UNAUTHORIZED), title="Song", artist="Artist"
        )

    (record,) = caplog.records
    formatted = JsonLineFormatter().format(record)
    assert "api.example.com" in formatted
    assert SECRET_KEY_VALUE not in formatted


def test_report_source_failure_captures_the_exception(mocker):
    capture_mock = mocker.patch.object(error_reporting.sentry_sdk, "capture_exception")
    error = RuntimeError("musicbrainz is down")

    error_reporting.report_source_failure("musicbrainz", error, title="Song", artist="Artist")

    capture_mock.assert_called_once_with(error)


def test_report_openai_failure_captures_the_exception(mocker):
    capture_mock = mocker.patch.object(error_reporting.sentry_sdk, "capture_exception")
    error = RuntimeError("OpenAI is down")

    error_reporting.report_openai_failure(error)

    capture_mock.assert_called_once_with(error)


def test_a_transient_openai_failure_is_only_a_warning(mocker, caplog):
    capture_mock = mocker.patch.object(error_reporting.sentry_sdk, "capture_exception")

    with caplog.at_level(logging.INFO):
        error_reporting.report_openai_failure(_openai_status_error(HTTP_TOO_MANY_REQUESTS))

    (record,) = caplog.records
    assert record.levelno == logging.WARNING
    capture_mock.assert_not_called()
