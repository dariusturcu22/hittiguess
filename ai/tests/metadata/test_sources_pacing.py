import threading
import time

from app.metadata.sources.pacing import RequestPacer

INTERVAL_SECONDS = 0.05
CONCURRENT_CALLER_COUNT = 4
TIMING_TOLERANCE_FRACTION = 0.8


def test_the_first_request_goes_out_immediately(mocker):
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")

    RequestPacer().wait(INTERVAL_SECONDS)

    sleep.assert_not_called()


def test_concurrent_callers_each_get_their_own_slot():
    pacer = RequestPacer()
    release_times: list[float] = []
    release_lock = threading.Lock()
    start_barrier = threading.Barrier(CONCURRENT_CALLER_COUNT)

    def caller() -> None:
        start_barrier.wait()
        pacer.wait(INTERVAL_SECONDS)
        with release_lock:
            release_times.append(time.monotonic())

    threads = [threading.Thread(target=caller) for unused_index in range(CONCURRENT_CALLER_COUNT)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    ordered_release_times = sorted(release_times)
    gaps = [later - earlier for earlier, later in zip(ordered_release_times, ordered_release_times[1:])]
    assert len(gaps) == CONCURRENT_CALLER_COUNT - 1
    # A small tolerance absorbs scheduler jitter between the sleep ending and the timestamp.
    assert all(gap >= INTERVAL_SECONDS * TIMING_TOLERANCE_FRACTION for gap in gaps)
