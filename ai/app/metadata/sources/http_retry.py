import time
from typing import Callable

import httpx

DEFAULT_MAX_RETRIES = 4
DEFAULT_BASE_DELAY_SECONDS = 5.0
BACKOFF_MULTIPLIER = 2
RETRYABLE_HTTP_STATUS_CODES = (429, 503)


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
    failures, honoring a Retry-After header when the server sends one and
    backing off exponentially otherwise. Pass an httpx.Client to reuse an
    existing session (for example, one already holding an authenticated
    login's cookies) instead of a one-off anonymous request. on_retry, if
    given, is called once per retry, so a caller pacing its own request rate
    can react to real observed failures instead of a fixed guess."""
    requester = client.get if client is not None else httpx.get
    last_transport_error: httpx.TransportError | None = None

    for attempt_number in range(max_retries):
        try:
            response = requester(url, params=params, headers=headers, timeout=timeout)
        except httpx.TransportError as transport_error:
            last_transport_error = transport_error
            if on_retry is not None:
                on_retry()
            time.sleep(base_delay_seconds * (BACKOFF_MULTIPLIER**attempt_number))
            continue

        if response.status_code in RETRYABLE_HTTP_STATUS_CODES:
            if on_retry is not None:
                on_retry()
            retry_after_header = response.headers.get("Retry-After")
            minimum_wait_seconds = base_delay_seconds * (BACKOFF_MULTIPLIER**attempt_number)
            wait_seconds = (
                max(float(retry_after_header), minimum_wait_seconds) if retry_after_header else minimum_wait_seconds
            )
            time.sleep(wait_seconds)
            continue

        response.raise_for_status()
        return response

    if last_transport_error is not None:
        raise last_transport_error
    response.raise_for_status()
    return response
