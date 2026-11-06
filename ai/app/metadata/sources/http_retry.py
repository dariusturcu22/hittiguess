import math
import time
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from typing import Callable

import httpx

DEFAULT_MAX_RETRIES = 4
DEFAULT_BASE_DELAY_SECONDS = 5.0
BACKOFF_MULTIPLIER = 2
RETRYABLE_HTTP_STATUS_CODES = (429, 503)
MAX_RETRY_AFTER_SECONDS = 60.0


def parse_retry_after_seconds(retry_after_header: str | None) -> float | None:
    """Seconds to wait per a Retry-After header, which is either delta-seconds
    or an HTTP-date. Returns None when absent or unparseable. The result is
    never negative and never exceeds MAX_RETRY_AFTER_SECONDS."""
    if not retry_after_header:
        return None
    try:
        wait_seconds = float(retry_after_header)
    except ValueError:
        try:
            retry_at = parsedate_to_datetime(retry_after_header)
        except (TypeError, ValueError):
            return None
        if retry_at.tzinfo is None:
            retry_at = retry_at.replace(tzinfo=timezone.utc)
        wait_seconds = (retry_at - datetime.now(timezone.utc)).total_seconds()
    if math.isnan(wait_seconds):
        return None
    return min(max(wait_seconds, 0.0), MAX_RETRY_AFTER_SECONDS)


def get_with_backoff(
    url: str,
    *,
    timeout: float,
    params: dict | None = None,
    headers: dict | None = None,
    client: httpx.Client | None = None,
    max_retries: int = DEFAULT_MAX_RETRIES,
    base_delay_seconds: float = DEFAULT_BASE_DELAY_SECONDS,
    on_retry: Callable[[], None] | None = None,
) -> httpx.Response:
    """GET with retry/backoff on rate-limit responses (429/503) and transport
    failures, honoring a Retry-After header (delta-seconds or HTTP-date,
    capped at MAX_RETRY_AFTER_SECONDS) when the server sends one and backing
    off exponentially otherwise. No wait follows the final attempt, and the
    error raised is the one from that final attempt. Pass an httpx.Client to
    reuse an existing session (for example, one already holding an
    authenticated login's cookies) instead of a one-off anonymous request.
    on_retry, if given, is called once per failed attempt, so a caller pacing
    its own request rate can react to real observed failures instead of a
    fixed guess."""
    requester = client.get if client is not None else httpx.get
    last_failure: httpx.TransportError | httpx.Response | None = None

    for attempt_number in range(max_retries):
        backoff_seconds = base_delay_seconds * (BACKOFF_MULTIPLIER**attempt_number)
        is_last_attempt = attempt_number == max_retries - 1
        try:
            response = requester(url, params=params, headers=headers, timeout=timeout)
        except httpx.TransportError as transport_error:
            last_failure = transport_error
            if on_retry is not None:
                on_retry()
            if not is_last_attempt:
                time.sleep(backoff_seconds)
            continue

        if response.status_code in RETRYABLE_HTTP_STATUS_CODES:
            last_failure = response
            if on_retry is not None:
                on_retry()
            if not is_last_attempt:
                retry_after_seconds = parse_retry_after_seconds(response.headers.get("Retry-After"))
                time.sleep(max(retry_after_seconds or 0.0, backoff_seconds))
            continue

        response.raise_for_status()
        return response

    if isinstance(last_failure, httpx.Response):
        last_failure.raise_for_status()
        return last_failure
    if last_failure is not None:
        raise last_failure
    raise ValueError("max_retries must be at least 1")
