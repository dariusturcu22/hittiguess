import sys
import time

import httpx

USER_AGENT = "hittiguess/0.1 (+https://hittiguess.com; contact@hittiguess.com)"

sys.stdout.reconfigure(encoding="utf-8")


def get_with_backoff(url: str, *, params: dict | None = None, headers: dict | None = None,
                      max_retries: int = 4, base_delay_seconds: float = 5.0) -> httpx.Response:
    """GET with retry/backoff on 429/503, the two statuses MusicBrainz and Discogs
    both use for rate-limit and temporary-block responses. Honors Retry-After
    when the server sends one, otherwise backs off exponentially."""
    for attempt in range(max_retries):
        response = httpx.get(url, params=params, headers=headers, timeout=10.0)
        if response.status_code in (429, 503):
            retry_after = response.headers.get("Retry-After")
            minimum_wait = base_delay_seconds * (2**attempt)
            wait_seconds = max(float(retry_after), minimum_wait) if retry_after else minimum_wait
            print(f"  [{response.status_code}, backing off {wait_seconds:.1f}s before retry {attempt + 1}/{max_retries}]")
            time.sleep(wait_seconds)
            continue
        response.raise_for_status()
        return response

    response.raise_for_status()
    return response
