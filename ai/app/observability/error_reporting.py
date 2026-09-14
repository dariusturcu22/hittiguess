import logging

import sentry_sdk

logger = logging.getLogger(__name__)


def report_source_failure(source_name: str, error: Exception, title: str, artist: str) -> None:
    """Surfaces a single metadata source's fetch failure distinctly, rather than
    letting it disappear into the pipeline's generic swallowed error response.
    sentry_sdk.capture_exception is a no-op when no DSN has been configured."""
    logger.error(
        "Metadata source fetch failed: %s",
        source_name,
        exc_info=error,
        extra={"source": source_name, "song_title": title, "song_artist": artist},
    )
    sentry_sdk.capture_exception(error)


def report_openai_failure(error: Exception) -> None:
    """Surfaces an OpenAI synthesis call failure distinctly, rather than letting
    it disappear into the pipeline's generic swallowed error response."""
    logger.error("OpenAI synthesis call failed", exc_info=error)
    sentry_sdk.capture_exception(error)
