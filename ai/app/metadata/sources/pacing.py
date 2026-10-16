import threading
import time
from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar

from app.metadata.sources import pace_state
from app.metadata.sources.pace_state import PaceStoreUnavailable, PaceWaitTooLong

_maximum_wait_seconds: ContextVar[float | None] = ContextVar("maximum_pace_wait_seconds", default=None)


@contextmanager
def waits_capped_at(maximum_wait_seconds: float) -> Iterator[None]:
    """Inside this block a pacer raises PaceWaitTooLong, reserving nothing, instead of
    sleeping longer than maximum_wait_seconds. Outside it every wait is slept."""
    token = _maximum_wait_seconds.set(maximum_wait_seconds)
    try:
        yield
    finally:
        _maximum_wait_seconds.reset(token)


class RequestPacer:
    """Spaces one source's requests across every thread calling it. Each caller
    reserves the next free slot under a lock, then sleeps until that slot outside
    it, so concurrent songs queue behind each other at the source's pace instead of
    all sleeping the same delay and firing together. The interval is passed per call
    because some sources adapt their pace as they go."""

    def __init__(self, stage: str | None = None) -> None:
        self._stage = stage
        self._lock = threading.Lock()
        self._next_slot = 0.0

    def wait(self, interval_seconds: float, maximum_wait_seconds: float | None = None) -> None:
        with self._lock:
            current_time = time.monotonic()
            reserved_slot = max(current_time, self._next_slot)
            remaining_seconds = reserved_slot - current_time
            if maximum_wait_seconds is not None and remaining_seconds > maximum_wait_seconds:
                raise PaceWaitTooLong(remaining_seconds, self._stage)
            self._next_slot = reserved_slot + interval_seconds
        if remaining_seconds > 0:
            time.sleep(remaining_seconds)

    def hold(self, pause_seconds: float) -> None:
        """Pushes the next free slot at least pause_seconds out, for every caller, when
        the source signals it wants a break (a 429, a usage breach). Slots already
        reserved before the hold aren't moved."""
        with self._lock:
            self._next_slot = max(self._next_slot, time.monotonic() + pause_seconds)


class PersistedRequestPacer:
    """RequestPacer's slots and holds for one pipeline stage, kept in its stage_pace
    row so every process shares them and they survive restarts. When the row cannot
    be used, the same calls fall back to an in-memory RequestPacer, so pacing never
    blocks or breaks a request."""

    def __init__(self, stage: str) -> None:
        self.stage = stage
        self._fallback = RequestPacer(stage)

    def wait(self, interval_seconds: float) -> None:
        maximum_wait_seconds = _maximum_wait_seconds.get()
        store = pace_state.shared_store
        if store is None:
            self._fallback.wait(interval_seconds, maximum_wait_seconds)
            return
        try:
            reserved_slot = store.reserve_slot(self.stage, interval_seconds, maximum_wait_seconds)
        except PaceStoreUnavailable:
            self._fallback.wait(interval_seconds, maximum_wait_seconds)
            return
        if reserved_slot.wait_seconds > 0:
            time.sleep(reserved_slot.wait_seconds)

    def hold(self, pause_seconds: float) -> None:
        self._fallback.hold(pause_seconds)
        store = pace_state.shared_store
        if store is None:
            return
        try:
            store.hold(self.stage, pause_seconds)
        except PaceStoreUnavailable:
            pass

    def record_response_time(self, elapsed_seconds: float) -> None:
        record_stage_response_time(self.stage, elapsed_seconds)


def record_stage_response_time(stage: str, elapsed_seconds: float) -> None:
    store = pace_state.shared_store
    if store is not None:
        store.record_response_time(stage, elapsed_seconds)
