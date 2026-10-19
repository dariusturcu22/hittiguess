"""Turns a stage's failure into the small error body core classifies. The body never
carries the exception text, which can hold request URLs and their keys."""

import logging
import math
from collections.abc import Iterator
from contextlib import contextmanager

import httpx
import openai
from fastapi import status
from fastapi.responses import JSONResponse

from app.metadata.sources.pace_state import PaceWaitTooLong
from app.metadata.sources.youtube import VideoUnavailableError
from app.metadata.stages.schemas import (
    ProviderHint,
    StageErrorBody,
    StageFailureCause,
    StagePaceLimitBody,
    StageProvider,
)
from app.observability.error_reporting import report_source_failure

logger = logging.getLogger(__name__)

RETRY_AFTER_HEADER = "Retry-After"
MINIMUM_RETRY_AFTER_SECONDS = 1
UNKNOWN_SUBJECT = "unknown"
PACE_LIMIT_MESSAGE = "The source's next free slot is further away than a stage call waits"

PROVIDER_HINTS = {
    StageProvider.YOUTUBE: ProviderHint.YOUTUBE_VIDEO_LOOKUP,
    StageProvider.DISCOGS: ProviderHint.API_KEY_PROVIDER,
    StageProvider.DEEPINFRA: ProviderHint.API_KEY_PROVIDER,
    StageProvider.OPENAI: ProviderHint.API_KEY_PROVIDER,
    StageProvider.MUSICBRAINZ: ProviderHint.KEYLESS_PROVIDER,
    StageProvider.WIKIDATA: ProviderHint.KEYLESS_PROVIDER,
    StageProvider.WIKIPEDIA: ProviderHint.KEYLESS_PROVIDER,
}

RESPONSE_STATUS_BY_CAUSE = {
    StageFailureCause.HTTP_ERROR: status.HTTP_502_BAD_GATEWAY,
    StageFailureCause.TIMEOUT: status.HTTP_504_GATEWAY_TIMEOUT,
    StageFailureCause.CONNECTION_ERROR: status.HTTP_503_SERVICE_UNAVAILABLE,
    StageFailureCause.NO_USABLE_METADATA: status.HTTP_422_UNPROCESSABLE_CONTENT,
    StageFailureCause.OTHER: status.HTTP_500_INTERNAL_SERVER_ERROR,
}

MESSAGE_BY_CAUSE = {
    StageFailureCause.HTTP_ERROR: "The provider answered with an error status",
    StageFailureCause.TIMEOUT: "The provider did not answer in time",
    StageFailureCause.CONNECTION_ERROR: "The provider could not be reached",
    StageFailureCause.NO_USABLE_METADATA: "The provider has no usable metadata for this video",
    StageFailureCause.OTHER: "The stage failed unexpectedly",
}


class StageFailure(Exception):
    def __init__(self, provider: StageProvider | None, error: Exception) -> None:
        super().__init__(type(error).__name__)
        self.provider = provider
        self.error = error


@contextmanager
def failures_attributed_to(provider: StageProvider) -> Iterator[None]:
    """Marks any failure inside the block as coming from provider. An over-cap pace
    wait is not a failure and passes through unchanged."""
    try:
        yield
    except (PaceWaitTooLong, StageFailure):
        raise
    except Exception as error:
        raise StageFailure(provider, error) from error


def classify(error: Exception) -> tuple[StageFailureCause, int | None]:
    if isinstance(error, VideoUnavailableError):
        return StageFailureCause.NO_USABLE_METADATA, None
    if isinstance(error, httpx.HTTPStatusError):
        return StageFailureCause.HTTP_ERROR, error.response.status_code
    if isinstance(error, httpx.TimeoutException):
        return StageFailureCause.TIMEOUT, None
    if isinstance(error, httpx.TransportError):
        return StageFailureCause.CONNECTION_ERROR, None
    if isinstance(error, openai.APITimeoutError):
        return StageFailureCause.TIMEOUT, None
    if isinstance(error, openai.APIConnectionError):
        return StageFailureCause.CONNECTION_ERROR, None
    if isinstance(error, openai.APIStatusError):
        return StageFailureCause.HTTP_ERROR, error.status_code
    return StageFailureCause.OTHER, None


def failure_response(stage: str, failure: StageFailure, title: str | None, artist: str | None) -> JSONResponse:
    cause, upstream_status = classify(failure.error)
    report_source_failure(stage, failure.error, title or UNKNOWN_SUBJECT, artist or UNKNOWN_SUBJECT)
    body = StageErrorBody(
        stage=stage,
        provider=failure.provider,
        provider_hint=PROVIDER_HINTS.get(failure.provider, ProviderHint.KEYLESS_PROVIDER),
        cause=cause,
        upstream_status=upstream_status,
        message=MESSAGE_BY_CAUSE[cause],
    )
    return JSONResponse(status_code=RESPONSE_STATUS_BY_CAUSE[cause], content=body.model_dump(mode="json", by_alias=True))


def pace_limit_response(stage: str, pace_wait: PaceWaitTooLong) -> JSONResponse:
    retry_after_seconds = max(MINIMUM_RETRY_AFTER_SECONDS, math.ceil(pace_wait.retry_after_seconds))
    logger.info("Stage %s deferred, next free slot in %s seconds", stage, retry_after_seconds)
    body = StagePaceLimitBody(stage=stage, retry_after_seconds=retry_after_seconds, message=PACE_LIMIT_MESSAGE)
    return JSONResponse(
        status_code=status.HTTP_429_TOO_MANY_REQUESTS,
        content=body.model_dump(mode="json", by_alias=True),
        headers={RETRY_AFTER_HEADER: str(retry_after_seconds)},
    )
