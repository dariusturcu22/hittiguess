import logging
from datetime import datetime, timezone

import pytest

from app.metadata.sources import pace_state, pacing
from app.metadata.sources.pace_state import (
    CURRENT_WAIT_SQL,
    HOLD_SQL,
    RECORD_RESPONSE_TIME_SQL,
    RESERVE_SLOT_SQL,
    RESERVE_SLOT_WITHIN_WAIT_SQL,
    RESPONSE_TIME_SMOOTHING_WEIGHT,
    STAGE_MUSICBRAINZ,
    UNAVAILABLE_RETRY_SECONDS,
    PaceStateStore,
    PaceStoreUnavailable,
    PaceWaitTooLong,
    ReservedSlot,
)
from app.metadata.sources.pacing import PersistedRequestPacer, waits_capped_at

INTERVAL_SECONDS = 1.5
INTERVAL_MILLISECONDS = 1500
MAXIMUM_WAIT_SECONDS = 30.0
RESERVED_WAIT_SECONDS = 4.25
CURRENT_WAIT_SECONDS = 42.0
PAUSE_SECONDS = 8.0
ELAPSED_SECONDS = 0.3456
ELAPSED_MILLISECONDS = 346
RESERVED_SLOT_AT = datetime(2026, 10, 16, 12, 0, tzinfo=timezone.utc)
CLOCK_START_SECONDS = 1000.0
SECRET_DATABASE_PASSWORD = "database-password-value"


class FakeConnection:
    """Records each statement and answers from a queue of prepared row lists."""

    def __init__(self, answers=None, error=None):
        self.statements: list[tuple[str, dict]] = []
        self.answers = list(answers or [])
        self.error = error
        self.closed = False

    def run(self, sql, **parameters):
        self.statements.append((sql, parameters))
        if self.error is not None:
            raise self.error
        return self.answers.pop(0) if self.answers else []

    def close(self):
        self.closed = True


class SynchronousExecutor:
    def submit(self, function, *arguments):
        function(*arguments)


class FakeClock:
    def __init__(self):
        self.now = CLOCK_START_SECONDS

    def __call__(self):
        return self.now


def _store(connection, clock=None):
    connections_opened: list[FakeConnection] = []

    def connect():
        connections_opened.append(connection)
        return connection

    store = PaceStateStore(connect=connect, clock=clock or FakeClock(), response_time_writer=SynchronousExecutor())
    return store, connections_opened


def test_reserving_a_slot_runs_one_atomic_update_and_returns_the_wait():
    connection = FakeConnection(answers=[[[RESERVED_SLOT_AT, RESERVED_WAIT_SECONDS]]])
    store, unused_connections = _store(connection)

    reserved_slot = store.reserve_slot(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, None)

    assert reserved_slot == ReservedSlot(slot_at=RESERVED_SLOT_AT, wait_seconds=RESERVED_WAIT_SECONDS)
    ((sql, parameters),) = connection.statements
    assert sql == RESERVE_SLOT_SQL
    assert parameters == {"stage": STAGE_MUSICBRAINZ, "interval_seconds": INTERVAL_SECONDS, "delay_ms": INTERVAL_MILLISECONDS}


def test_the_reservation_moves_next_slot_from_the_later_of_now_and_the_previous_slot():
    assert "SET next_slot_at = GREATEST(now(), next_slot_at) + make_interval" in RESERVE_SLOT_SQL
    assert "WHERE stage = :stage" in RESERVE_SLOT_SQL


def test_a_slot_already_past_never_produces_a_negative_wait():
    connection = FakeConnection(answers=[[[RESERVED_SLOT_AT, -RESERVED_WAIT_SECONDS]]])
    store, unused_connections = _store(connection)

    assert store.reserve_slot(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, None).wait_seconds == 0


def test_a_capped_reservation_within_the_cap_uses_the_conditional_update():
    connection = FakeConnection(answers=[[[RESERVED_SLOT_AT, RESERVED_WAIT_SECONDS]]])
    store, unused_connections = _store(connection)

    store.reserve_slot(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, MAXIMUM_WAIT_SECONDS)

    ((sql, parameters),) = connection.statements
    assert sql == RESERVE_SLOT_WITHIN_WAIT_SQL
    assert parameters["maximum_wait_seconds"] == MAXIMUM_WAIT_SECONDS


def test_a_capped_reservation_over_the_cap_reserves_nothing_and_reports_the_current_wait():
    connection = FakeConnection(answers=[[], [[CURRENT_WAIT_SECONDS]]])
    store, unused_connections = _store(connection)

    with pytest.raises(PaceWaitTooLong) as raised:
        store.reserve_slot(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, MAXIMUM_WAIT_SECONDS)

    assert raised.value.retry_after_seconds == CURRENT_WAIT_SECONDS
    assert raised.value.stage == STAGE_MUSICBRAINZ
    executed_sql = [sql for sql, unused_parameters in connection.statements]
    assert executed_sql == [RESERVE_SLOT_WITHIN_WAIT_SQL, CURRENT_WAIT_SQL]


def test_a_missing_stage_row_makes_the_store_unavailable():
    connection = FakeConnection(answers=[[]])
    store, unused_connections = _store(connection)

    with pytest.raises(PaceStoreUnavailable):
        store.reserve_slot(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, None)


def test_hold_pushes_the_next_slot_and_sets_the_cooldown():
    connection = FakeConnection()
    store, unused_connections = _store(connection)

    store.hold(STAGE_MUSICBRAINZ, PAUSE_SECONDS)

    ((sql, parameters),) = connection.statements
    assert sql == HOLD_SQL
    assert "cooldown_until = GREATEST(cooldown_until" in sql
    assert parameters == {"stage": STAGE_MUSICBRAINZ, "pause_seconds": PAUSE_SECONDS}


def test_a_response_time_is_folded_into_the_moving_average_in_milliseconds():
    connection = FakeConnection()
    store, unused_connections = _store(connection)

    store.record_response_time(STAGE_MUSICBRAINZ, ELAPSED_SECONDS)

    ((sql, parameters),) = connection.statements
    assert sql == RECORD_RESPONSE_TIME_SQL
    assert parameters == {
        "stage": STAGE_MUSICBRAINZ,
        "sample_ms": ELAPSED_MILLISECONDS,
        "sample_weight": RESPONSE_TIME_SMOOTHING_WEIGHT,
    }


def test_the_connection_is_opened_once_and_reused():
    connection = FakeConnection(answers=[[[RESERVED_SLOT_AT, RESERVED_WAIT_SECONDS]], [[RESERVED_SLOT_AT, RESERVED_WAIT_SECONDS]]])
    store, connections_opened = _store(connection)

    store.reserve_slot(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, None)
    store.reserve_slot(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, None)

    assert len(connections_opened) == 1


def test_a_database_error_closes_the_connection_and_skips_the_database_until_the_retry_window_ends(caplog):
    clock = FakeClock()
    failing_connection = FakeConnection(error=ConnectionRefusedError(f"password={SECRET_DATABASE_PASSWORD}"))
    store, connections_opened = _store(failing_connection, clock)

    with caplog.at_level(logging.WARNING), pytest.raises(PaceStoreUnavailable):
        store.reserve_slot(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, None)
    assert failing_connection.closed
    with pytest.raises(PaceStoreUnavailable):
        store.reserve_slot(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, None)
    assert len(connections_opened) == 1

    clock.now += UNAVAILABLE_RETRY_SECONDS
    with pytest.raises(PaceStoreUnavailable):
        store.reserve_slot(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, None)
    assert len(connections_opened) == 2

    assert "ConnectionRefusedError" in caplog.text
    assert SECRET_DATABASE_PASSWORD not in caplog.text


def test_a_failed_response_time_write_is_dropped_quietly():
    store, unused_connections = _store(FakeConnection(error=OSError("down")))

    store.record_response_time(STAGE_MUSICBRAINZ, ELAPSED_SECONDS)


class FakeStore:
    def __init__(self, wait_seconds=0.0, error=None):
        self.wait_seconds = wait_seconds
        self.error = error
        self.reservations: list[tuple[str, float, float | None]] = []
        self.holds: list[tuple[str, float]] = []
        self.response_times: list[tuple[str, float]] = []

    def reserve_slot(self, stage, interval_seconds, maximum_wait_seconds):
        self.reservations.append((stage, interval_seconds, maximum_wait_seconds))
        if self.error is not None:
            raise self.error
        return ReservedSlot(slot_at=RESERVED_SLOT_AT, wait_seconds=self.wait_seconds)

    def hold(self, stage, pause_seconds):
        self.holds.append((stage, pause_seconds))
        if self.error is not None:
            raise self.error

    def record_response_time(self, stage, elapsed_seconds):
        self.response_times.append((stage, elapsed_seconds))


def test_the_pacer_sleeps_until_the_slot_the_database_reserved(mocker, monkeypatch):
    store = FakeStore(wait_seconds=RESERVED_WAIT_SECONDS)
    monkeypatch.setattr(pace_state, "shared_store", store)
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")

    PersistedRequestPacer(STAGE_MUSICBRAINZ).wait(INTERVAL_SECONDS)

    assert store.reservations == [(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, None)]
    sleep.assert_called_once_with(RESERVED_WAIT_SECONDS)


def test_the_pacer_falls_back_to_in_memory_slots_when_the_store_is_unavailable(mocker, monkeypatch):
    monkeypatch.setattr(pace_state, "shared_store", FakeStore(error=PaceStoreUnavailable()))
    mocker.patch("app.metadata.sources.pacing.time.monotonic", return_value=CLOCK_START_SECONDS)
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")
    pacer = PersistedRequestPacer(STAGE_MUSICBRAINZ)

    pacer.wait(INTERVAL_SECONDS)
    pacer.wait(INTERVAL_SECONDS)

    sleep.assert_called_once_with(pytest.approx(INTERVAL_SECONDS))


def test_a_hold_reaches_the_store_and_the_in_memory_fallback(mocker, monkeypatch):
    store = FakeStore()
    monkeypatch.setattr(pace_state, "shared_store", store)
    mocker.patch("app.metadata.sources.pacing.time.monotonic", return_value=CLOCK_START_SECONDS)
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")
    pacer = PersistedRequestPacer(STAGE_MUSICBRAINZ)

    pacer.hold(PAUSE_SECONDS)
    store.error = PaceStoreUnavailable()
    pacer.wait(INTERVAL_SECONDS)

    assert store.holds == [(STAGE_MUSICBRAINZ, PAUSE_SECONDS)]
    sleep.assert_called_once_with(pytest.approx(PAUSE_SECONDS))


def test_a_hold_with_the_store_unavailable_still_holds_in_memory(mocker, monkeypatch):
    monkeypatch.setattr(pace_state, "shared_store", FakeStore(error=PaceStoreUnavailable()))
    mocker.patch("app.metadata.sources.pacing.time.monotonic", return_value=CLOCK_START_SECONDS)
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")
    pacer = PersistedRequestPacer(STAGE_MUSICBRAINZ)

    pacer.hold(PAUSE_SECONDS)
    pacer.wait(INTERVAL_SECONDS)

    sleep.assert_called_once_with(pytest.approx(PAUSE_SECONDS))


def test_a_capped_wait_passes_the_cap_to_the_store_and_propagates_an_over_cap_wait(mocker, monkeypatch):
    store = FakeStore(error=PaceWaitTooLong(CURRENT_WAIT_SECONDS, STAGE_MUSICBRAINZ))
    monkeypatch.setattr(pace_state, "shared_store", store)
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")

    with waits_capped_at(MAXIMUM_WAIT_SECONDS), pytest.raises(PaceWaitTooLong):
        PersistedRequestPacer(STAGE_MUSICBRAINZ).wait(INTERVAL_SECONDS)

    assert store.reservations == [(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, MAXIMUM_WAIT_SECONDS)]
    sleep.assert_not_called()


def test_outside_a_capped_block_the_pacer_waits_without_a_cap(monkeypatch, mocker):
    store = FakeStore()
    monkeypatch.setattr(pace_state, "shared_store", store)
    mocker.patch("app.metadata.sources.pacing.time.sleep")

    with waits_capped_at(MAXIMUM_WAIT_SECONDS):
        pass
    PersistedRequestPacer(STAGE_MUSICBRAINZ).wait(INTERVAL_SECONDS)

    assert store.reservations == [(STAGE_MUSICBRAINZ, INTERVAL_SECONDS, None)]


def test_the_in_memory_fallback_also_refuses_an_over_cap_wait_without_reserving(mocker):
    mocker.patch("app.metadata.sources.pacing.time.monotonic", return_value=CLOCK_START_SECONDS)
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")
    pacer = PersistedRequestPacer(STAGE_MUSICBRAINZ)
    pacer.hold(CURRENT_WAIT_SECONDS)

    with waits_capped_at(MAXIMUM_WAIT_SECONDS), pytest.raises(PaceWaitTooLong) as raised:
        pacer.wait(INTERVAL_SECONDS)
    pacer.wait(INTERVAL_SECONDS)

    assert raised.value.retry_after_seconds == pytest.approx(CURRENT_WAIT_SECONDS)
    assert raised.value.stage == STAGE_MUSICBRAINZ
    sleep.assert_called_once_with(pytest.approx(CURRENT_WAIT_SECONDS))


def test_response_times_reach_the_store_and_are_skipped_without_one(monkeypatch):
    store = FakeStore()
    monkeypatch.setattr(pace_state, "shared_store", store)

    PersistedRequestPacer(STAGE_MUSICBRAINZ).record_response_time(ELAPSED_SECONDS)
    monkeypatch.setattr(pace_state, "shared_store", None)
    pacing.record_stage_response_time(STAGE_MUSICBRAINZ, ELAPSED_SECONDS)

    assert store.response_times == [(STAGE_MUSICBRAINZ, ELAPSED_SECONDS)]
