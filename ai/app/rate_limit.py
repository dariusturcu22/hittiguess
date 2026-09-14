import threading
import time
from collections import defaultdict, deque

from fastapi import HTTPException, Request, status

REQUEST_WINDOW_SECONDS = 60.0
METADATA_RESOLVE_MAX_REQUESTS_PER_WINDOW = 30
UNKNOWN_CLIENT_ADDRESS_KEY = "unknown"


class SlidingWindowRateLimiter:
    """In-memory, single-process sliding-window limiter. A distributed deployment
    would need the request log shared across processes instead of held in memory."""

    def __init__(self, max_requests_per_window: int, window_seconds: float) -> None:
        self._max_requests_per_window = max_requests_per_window
        self._window_seconds = window_seconds
        self._lock = threading.Lock()
        self._request_timestamps_by_key: dict[str, deque[float]] = defaultdict(deque)

    def allow(self, key: str) -> bool:
        current_time = time.monotonic()
        window_start = current_time - self._window_seconds
        with self._lock:
            request_timestamps = self._request_timestamps_by_key[key]
            while request_timestamps and request_timestamps[0] < window_start:
                request_timestamps.popleft()

            if len(request_timestamps) >= self._max_requests_per_window:
                return False

            request_timestamps.append(current_time)
            return True

    def reset(self) -> None:
        with self._lock:
            self._request_timestamps_by_key.clear()


metadata_resolve_rate_limiter = SlidingWindowRateLimiter(
    METADATA_RESOLVE_MAX_REQUESTS_PER_WINDOW, REQUEST_WINDOW_SECONDS
)


def enforce_metadata_resolve_rate_limit(request: Request) -> None:
    client_address = request.client.host if request.client else UNKNOWN_CLIENT_ADDRESS_KEY
    if not metadata_resolve_rate_limiter.allow(client_address):
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="Rate limit exceeded")
