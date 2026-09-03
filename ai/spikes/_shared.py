import sys
import time

import httpx

USER_AGENT = "hittiguess/0.1 (+https://hittiguess.com; contact@hittiguess.com)"

MUSICBRAINZ_DELAY_SECONDS = 2.5  # documented hard limit is 1 request/second; padded well above it
DISCOGS_DELAY_SECONDS = 1.1  # well under the 60/minute authenticated limit
# Wikidata's published limit for anonymous requests with no identifying characteristics
# is 10/minute (a descriptive User-Agent may or may not move a script into the more
# lenient 200/minute browser-identified tier, not confirmed either way), so pace to the
# stricter number rather than assume the better one applies.
WIKIDATA_DELAY_SECONDS = 6.5

DEFAULT_MAX_RETRIES = 4
DEFAULT_BASE_DELAY_SECONDS = 5.0
BACKOFF_MULTIPLIER = 2
RETRYABLE_HTTP_STATUS_CODES = (429, 503)
REQUEST_TIMEOUT_SECONDS = 10.0
YEAR_DIGIT_COUNT = 4

sys.stdout.reconfigure(encoding="utf-8")


def get_with_backoff(url: str, *, params: dict | None = None, headers: dict | None = None,
                      max_retries: int = DEFAULT_MAX_RETRIES,
                      base_delay_seconds: float = DEFAULT_BASE_DELAY_SECONDS) -> httpx.Response:
    """GET with retry/backoff on rate-limit responses and transport-level
    failures. Honors Retry-After when the server sends one, otherwise backs
    off exponentially."""
    last_transport_error: httpx.TransportError | None = None
    for attempt_number in range(max_retries):
        try:
            response = httpx.get(url, params=params, headers=headers, timeout=REQUEST_TIMEOUT_SECONDS)
        except httpx.TransportError as transport_error:
            last_transport_error = transport_error
            wait_seconds = base_delay_seconds * (BACKOFF_MULTIPLIER**attempt_number)
            print(
                f"  [{type(transport_error).__name__}, backing off {wait_seconds:.1f}s "
                f"before retry {attempt_number + 1}/{max_retries}]"
            )
            time.sleep(wait_seconds)
            continue

        if response.status_code in RETRYABLE_HTTP_STATUS_CODES:
            retry_after_header = response.headers.get("Retry-After")
            minimum_wait_seconds = base_delay_seconds * (BACKOFF_MULTIPLIER**attempt_number)
            wait_seconds = (
                max(float(retry_after_header), minimum_wait_seconds) if retry_after_header else minimum_wait_seconds
            )
            print(
                f"  [{response.status_code}, backing off {wait_seconds:.1f}s "
                f"before retry {attempt_number + 1}/{max_retries}]"
            )
            time.sleep(wait_seconds)
            continue
        response.raise_for_status()
        return response

    if last_transport_error is not None:
        raise last_transport_error
    response.raise_for_status()
    return response


def extract_year(date_str: str | None) -> int | None:
    """Reads the year from either MusicBrainz's plain "YYYY"/"YYYY-MM-DD"
    format or Wikidata's "+YYYY-MM-DDT00:00:00Z" format, comparing by year
    rather than by the raw date string, since a partial-precision date can
    otherwise sort incorrectly against a fully-precise one."""
    if not date_str:
        return None
    digits_only = date_str.lstrip("+-")
    year_digits = digits_only[:YEAR_DIGIT_COUNT]
    return int(year_digits) if year_digits.isdigit() else None
