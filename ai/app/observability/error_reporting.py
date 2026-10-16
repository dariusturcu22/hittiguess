import logging

import httpx
import openai
import sentry_sdk

logger = logging.getLogger(__name__)

HTTP_TOO_MANY_REQUESTS = 429
FIRST_SERVER_ERROR_STATUS = 500
UPSTREAM_STATUS_UNKNOWN = "none"
OPENAI_SUBJECT = "OpenAI"


def _upstream_status(error: Exception) -> int | None:
    if isinstance(error, httpx.HTTPStatusError):
        return error.response.status_code
    if isinstance(error, openai.APIStatusError):
        return error.status_code
    return None


def is_transient_upstream_failure(error: Exception) -> bool:
    """A rate limit, a server error, a timeout, or a failed connection: the provider is busy or
    briefly unreachable and the same call is expected to succeed on a retry."""
    if isinstance(error, (httpx.TimeoutException, httpx.TransportError)):
        return True
    if isinstance(error, (openai.APITimeoutError, openai.APIConnectionError)):
        return True
    upstream_status = _upstream_status(error)
    return upstream_status is not None and (
        upstream_status == HTTP_TOO_MANY_REQUESTS or upstream_status >= FIRST_SERVER_ERROR_STATUS
    )


def _warn_transient(message: str, subject: str, error: Exception, extra: dict[str, str]) -> None:
    upstream_status = _upstream_status(error)
    logger.warning(
        "%s transiently: %s (%s, upstream status %s)",
        message,
        subject,
        type(error).__name__,
        upstream_status if upstream_status is not None else UPSTREAM_STATUS_UNKNOWN,
        extra=extra,
    )


def report_source_failure(source_name: str, error: Exception, title: str, artist: str) -> None:
    """Surfaces a single metadata source's fetch failure distinctly, rather than
    letting it disappear into the pipeline's generic swallowed error response. A
    transient upstream failure is a warning without a trace and is not sent to Sentry,
    since the caller retries it; anything else is an error with its trace.
    sentry_sdk.capture_exception is a no-op when no DSN has been configured."""
    extra = {"source": source_name, "song_title": title, "song_artist": artist}
    if is_transient_upstream_failure(error):
        _warn_transient("Metadata source fetch failed", source_name, error, extra)
        return
    logger.error("Metadata source fetch failed: %s", source_name, exc_info=error, extra=extra)
    sentry_sdk.capture_exception(error)


def report_openai_failure(error: Exception) -> None:
    """Surfaces an OpenAI synthesis call failure distinctly, rather than letting
    it disappear into the pipeline's generic swallowed error response. Transient
    upstream failures are warnings, as for report_source_failure."""
    if is_transient_upstream_failure(error):
        _warn_transient("OpenAI synthesis call failed", OPENAI_SUBJECT, error, {})
        return
    logger.error("OpenAI synthesis call failed", exc_info=error)
    sentry_sdk.capture_exception(error)
