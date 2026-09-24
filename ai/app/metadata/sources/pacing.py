import threading
import time


class RequestPacer:
    """Spaces one source's requests across every thread calling it. Each caller
    reserves the next free slot under a lock, then sleeps until that slot outside
    it, so concurrent songs queue behind each other at the source's pace instead of
    all sleeping the same delay and firing together. The interval is passed per call
    because some sources adapt their pace as they go."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._next_slot = 0.0

    def wait(self, interval_seconds: float) -> None:
        with self._lock:
            current_time = time.monotonic()
            reserved_slot = max(current_time, self._next_slot)
            self._next_slot = reserved_slot + interval_seconds
        remaining_seconds = reserved_slot - current_time
        if remaining_seconds > 0:
            time.sleep(remaining_seconds)

    def hold(self, pause_seconds: float) -> None:
        """Pushes the next free slot at least pause_seconds out, for every caller, when
        the source signals it wants a break (a 429, a usage breach). Slots already
        reserved before the hold aren't moved."""
        with self._lock:
            self._next_slot = max(self._next_slot, time.monotonic() + pause_seconds)
