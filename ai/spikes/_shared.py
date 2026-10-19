import sys
import time
from urllib.parse import urlparse

import httpx

USER_AGENT = "hittiguess/0.1 (+https://hittiguess.com; contact@hittiguess.com)"

RATE_LIMIT_TARGET_UTILIZATION = 0.67  # hard cap, stay comfortably inside each source's documented ceiling

MUSICBRAINZ_LIMIT_PER_MINUTE = 60  # documented hard limit is 1 req/sec, per IP
MUSICBRAINZ_DELAY_SECONDS = 60 / (MUSICBRAINZ_LIMIT_PER_MINUTE * RATE_LIMIT_TARGET_UTILIZATION)
# Wikidata and Discogs pace themselves internally instead of exposing a fixed constant
# here: Wikidata's rate depends on whether a bot-password login is configured (10/min
# anonymous vs 200/min authenticated, see wikidata_spike.py), and Discogs paces off the
# real X-Discogs-Ratelimit-* response headers it returns (see discogs_spike.py's
# DiscogsRateLimiter) rather than a guess, since it's the one source that actually
# tells you your live quota usage.

DEFAULT_MAX_RETRIES = 4
DEFAULT_BASE_DELAY_SECONDS = 5.0
BACKOFF_MULTIPLIER = 2
RETRYABLE_HTTP_STATUS_CODES = (429, 503)
REQUEST_TIMEOUT_SECONDS = 10.0
YEAR_DIGIT_COUNT = 4

sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)

# Keyed by "<status_code>" for an HTTP retry or the exception class name for a
# transport-level failure, incremented on every backoff, not just the final
# outcome, so a request that succeeds on its 3rd attempt still counts 2 retries.
RETRY_EVENT_COUNTS: dict[str, int] = {}

# Keyed by hostname, one sample per successful response, measuring only the
# actual request/response round trip, not our own pacing sleep beforehand
# or any retry backoff wait, those are separate costs, not API latency.
RESPONSE_TIME_SAMPLES: dict[str, list[float]] = {}


def get_with_backoff(url: str, *, params: dict | None = None, headers: dict | None = None,
                      client: httpx.Client | None = None,
                      max_retries: int = DEFAULT_MAX_RETRIES,
                      base_delay_seconds: float = DEFAULT_BASE_DELAY_SECONDS) -> httpx.Response:
    """GET with retry/backoff on rate-limit responses and transport-level
    failures. Honors Retry-After when the server sends one, otherwise backs
    off exponentially. Pass an httpx.Client (for example, one already
    holding an authenticated login session's cookies) to reuse it instead
    of a one-off anonymous request."""
    requester = client.get if client is not None else httpx.get
    host = urlparse(url).hostname or url
    last_transport_error: httpx.TransportError | None = None
    for attempt_number in range(max_retries):
        try:
            request_started_at = time.perf_counter()
            response = requester(url, params=params, headers=headers, timeout=REQUEST_TIMEOUT_SECONDS)
            request_elapsed_seconds = time.perf_counter() - request_started_at
        except httpx.TransportError as transport_error:
            last_transport_error = transport_error
            wait_seconds = base_delay_seconds * (BACKOFF_MULTIPLIER**attempt_number)
            event_key = type(transport_error).__name__
            RETRY_EVENT_COUNTS[event_key] = RETRY_EVENT_COUNTS.get(event_key, 0) + 1
            print(
                f"  [{event_key}, backing off {wait_seconds:.1f}s "
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
            event_key = str(response.status_code)
            RETRY_EVENT_COUNTS[event_key] = RETRY_EVENT_COUNTS.get(event_key, 0) + 1
            print(
                f"  [{response.status_code}, backing off {wait_seconds:.1f}s "
                f"before retry {attempt_number + 1}/{max_retries}]"
            )
            time.sleep(wait_seconds)
            continue
        response.raise_for_status()
        RESPONSE_TIME_SAMPLES.setdefault(host, []).append(request_elapsed_seconds)
        return response

    if last_transport_error is not None:
        raise last_transport_error
    response.raise_for_status()
    return response


def response_time_summary() -> dict[str, dict[str, float]]:
    """Per-host average/min/max/count of pure request/response latency,
    excluding pacing sleep and retry backoff, for reporting alongside the
    per-song wall-clock timing, which includes both."""
    summary = {}
    for host, samples in RESPONSE_TIME_SAMPLES.items():
        summary[host] = {
            "count": len(samples),
            "average_seconds": sum(samples) / len(samples),
            "min_seconds": min(samples),
            "max_seconds": max(samples),
        }
    return summary


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
