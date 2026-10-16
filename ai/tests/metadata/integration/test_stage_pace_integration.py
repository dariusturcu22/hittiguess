import threading
from datetime import timedelta

import pytest

from app.metadata.sources import pace_state
from app.metadata.sources.pace_state import (
    STAGE_DISCOGS,
    STAGE_MUSICBRAINZ,
    PaceStateStore,
    PaceWaitTooLong,
)
from app.metadata.sources.pacing import PersistedRequestPacer, waits_capped_at
from tests.metadata.integration.conftest import open_connection

CONCURRENT_RESERVER_COUNT = 6
# Long enough that every reservation after the first starts from the previous slot.
SPACING_INTERVAL_SECONDS = 10.0
SHORT_INTERVAL_SECONDS = 1.5
SHORT_INTERVAL_MILLISECONDS = 1500
LONG_INTERVAL_SECONDS = 60.0
MAXIMUM_WAIT_SECONDS = 30.0
PAUSE_SECONDS = 8.0
WAIT_TOLERANCE_SECONDS = 2.0
FIRST_SAMPLE_SECONDS = 0.1
SECOND_SAMPLE_SECONDS = 0.2
FIRST_SAMPLE_MILLISECONDS = 100
SMOOTHED_AVERAGE_MILLISECONDS = 120


class SynchronousExecutor:
    def submit(self, function, *arguments):
        function(*arguments)


def _store() -> PaceStateStore:
    return PaceStateStore(response_time_writer=SynchronousExecutor())


def _pace_row(connection_url: str, stage: str) -> dict:
    connection = open_connection(connection_url)
    try:
        ((next_slot_at, cooldown_until, current_delay_ms, avg_response_ms, database_now),) = connection.run(
            "SELECT next_slot_at, cooldown_until, current_delay_ms, avg_response_ms, now() FROM stage_pace WHERE stage = :stage",
            stage=stage,
        )
    finally:
        connection.close()
    return {
        "next_slot_at": next_slot_at,
        "cooldown_until": cooldown_until,
        "current_delay_ms": current_delay_ms,
        "avg_response_ms": avg_response_ms,
        "now": database_now,
    }


def test_the_first_reservation_on_an_idle_stage_needs_no_wait_and_records_the_delay(pace_database_url):
    reserved_slot = _store().reserve_slot(STAGE_MUSICBRAINZ, SHORT_INTERVAL_SECONDS, None)

    assert reserved_slot.wait_seconds == 0
    pace_row = _pace_row(pace_database_url, STAGE_MUSICBRAINZ)
    assert pace_row["current_delay_ms"] == SHORT_INTERVAL_MILLISECONDS
    assert pace_row["next_slot_at"] == reserved_slot.slot_at + timedelta(seconds=SHORT_INTERVAL_SECONDS)


def test_concurrent_reservations_get_strictly_increasing_slots_one_interval_apart(pace_database_url):
    start_barrier = threading.Barrier(CONCURRENT_RESERVER_COUNT)
    reserved_slots = []
    reserved_slots_lock = threading.Lock()

    def reserve() -> None:
        store = _store()
        start_barrier.wait()
        reserved_slot = store.reserve_slot(STAGE_MUSICBRAINZ, SPACING_INTERVAL_SECONDS, None)
        with reserved_slots_lock:
            reserved_slots.append(reserved_slot)

    threads = [threading.Thread(target=reserve) for unused_index in range(CONCURRENT_RESERVER_COUNT)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    slot_times = sorted(reserved_slot.slot_at for reserved_slot in reserved_slots)
    gaps = [later - earlier for earlier, later in zip(slot_times, slot_times[1:])]
    assert len(set(slot_times)) == CONCURRENT_RESERVER_COUNT
    assert gaps == [timedelta(seconds=SPACING_INTERVAL_SECONDS)] * (CONCURRENT_RESERVER_COUNT - 1)
    waits = sorted(reserved_slot.wait_seconds for reserved_slot in reserved_slots)
    assert waits[-1] == pytest.approx(SPACING_INTERVAL_SECONDS * (CONCURRENT_RESERVER_COUNT - 1), abs=WAIT_TOLERANCE_SECONDS)


def test_a_capped_reservation_over_the_cap_reports_the_wait_and_leaves_the_slot_alone(pace_database_url):
    store = _store()
    store.reserve_slot(STAGE_MUSICBRAINZ, LONG_INTERVAL_SECONDS, None)
    next_slot_before = _pace_row(pace_database_url, STAGE_MUSICBRAINZ)["next_slot_at"]

    with pytest.raises(PaceWaitTooLong) as raised:
        store.reserve_slot(STAGE_MUSICBRAINZ, SHORT_INTERVAL_SECONDS, MAXIMUM_WAIT_SECONDS)

    assert raised.value.retry_after_seconds == pytest.approx(LONG_INTERVAL_SECONDS, abs=WAIT_TOLERANCE_SECONDS)
    assert _pace_row(pace_database_url, STAGE_MUSICBRAINZ)["next_slot_at"] == next_slot_before


def test_a_capped_reservation_within_the_cap_reserves_the_slot(pace_database_url):
    store = _store()
    store.reserve_slot(STAGE_MUSICBRAINZ, SHORT_INTERVAL_SECONDS, None)

    reserved_slot = store.reserve_slot(STAGE_MUSICBRAINZ, SHORT_INTERVAL_SECONDS, MAXIMUM_WAIT_SECONDS)

    assert reserved_slot.wait_seconds == pytest.approx(SHORT_INTERVAL_SECONDS, abs=WAIT_TOLERANCE_SECONDS)


def test_a_hold_sets_the_cooldown_and_pushes_only_its_own_stage(pace_database_url):
    _store().hold(STAGE_DISCOGS, PAUSE_SECONDS)

    discogs_row = _pace_row(pace_database_url, STAGE_DISCOGS)
    pause = timedelta(seconds=PAUSE_SECONDS)
    tolerance = timedelta(seconds=WAIT_TOLERANCE_SECONDS)
    assert abs(discogs_row["cooldown_until"] - (discogs_row["now"] + pause)) < tolerance
    assert discogs_row["next_slot_at"] == discogs_row["cooldown_until"]
    assert _pace_row(pace_database_url, STAGE_MUSICBRAINZ)["cooldown_until"] is None


def test_response_times_form_an_exponential_moving_average(pace_database_url):
    store = _store()

    store.record_response_time(STAGE_MUSICBRAINZ, FIRST_SAMPLE_SECONDS)
    assert _pace_row(pace_database_url, STAGE_MUSICBRAINZ)["avg_response_ms"] == FIRST_SAMPLE_MILLISECONDS
    store.record_response_time(STAGE_MUSICBRAINZ, SECOND_SAMPLE_SECONDS)
    assert _pace_row(pace_database_url, STAGE_MUSICBRAINZ)["avg_response_ms"] == SMOOTHED_AVERAGE_MILLISECONDS


def test_pacers_in_different_processes_share_one_pace_through_the_table(pace_database_url, mocker, monkeypatch):
    monkeypatch.setattr(pace_state, "shared_store", _store())
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")
    first_pacer = PersistedRequestPacer(STAGE_MUSICBRAINZ)
    second_pacer = PersistedRequestPacer(STAGE_MUSICBRAINZ)

    first_pacer.wait(LONG_INTERVAL_SECONDS)
    with waits_capped_at(MAXIMUM_WAIT_SECONDS), pytest.raises(PaceWaitTooLong):
        second_pacer.wait(SHORT_INTERVAL_SECONDS)
    second_pacer.wait(SHORT_INTERVAL_SECONDS)

    ((slept_seconds,), unused_keywords) = sleep.call_args
    assert slept_seconds == pytest.approx(LONG_INTERVAL_SECONDS, abs=WAIT_TOLERANCE_SECONDS)
