"""Pace and cooldown state of each stage that calls an external source, kept in the
core-owned stage_pace table so every AI service process shares one pace per source
and the spacing survives restarts. Any database failure raises PaceStoreUnavailable,
and the store then skips the database for a while, so callers fall back to pacing in
memory instead of waiting on a database that is down."""

import logging
import threading
import time
from collections.abc import Callable
from concurrent.futures import Executor, ThreadPoolExecutor
from datetime import datetime
from typing import NamedTuple

import pg8000.native

from app.config import settings
from app.dedup.database import _parse_database_url

logger = logging.getLogger(__name__)

STAGE_YOUTUBE_METADATA = "YOUTUBE_METADATA"
STAGE_MUSICBRAINZ = "MUSICBRAINZ"
STAGE_DISCOGS = "DISCOGS"
STAGE_WIKIDATA = "WIKIDATA"
STAGE_WIKIPEDIA = "WIKIPEDIA"

DATABASE_TIMEOUT_SECONDS = 2.0
UNAVAILABLE_RETRY_SECONDS = 30.0
# Weight of the newest sample in the response-time moving average.
RESPONSE_TIME_SMOOTHING_WEIGHT = 0.2
MILLISECONDS_PER_SECOND = 1000
NO_WAIT_SECONDS = 0.0
RESPONSE_TIME_WRITER_THREADS = 1

RESERVE_SLOT_SQL = """
    UPDATE stage_pace
    SET next_slot_at = GREATEST(now(), next_slot_at) + make_interval(secs => CAST(:interval_seconds AS double precision)),
        current_delay_ms = CAST(:delay_ms AS integer),
        updated_at = now()
    WHERE stage = :stage
    RETURNING next_slot_at - make_interval(secs => CAST(:interval_seconds AS double precision)),
        EXTRACT(EPOCH FROM next_slot_at - now()) - CAST(:interval_seconds AS double precision)
"""

RESERVE_SLOT_WITHIN_WAIT_SQL = """
    UPDATE stage_pace
    SET next_slot_at = GREATEST(now(), next_slot_at) + make_interval(secs => CAST(:interval_seconds AS double precision)),
        current_delay_ms = CAST(:delay_ms AS integer),
        updated_at = now()
    WHERE stage = :stage
        AND next_slot_at <= now() + make_interval(secs => CAST(:maximum_wait_seconds AS double precision))
    RETURNING next_slot_at - make_interval(secs => CAST(:interval_seconds AS double precision)),
        EXTRACT(EPOCH FROM next_slot_at - now()) - CAST(:interval_seconds AS double precision)
"""

CURRENT_WAIT_SQL = """
    SELECT GREATEST(EXTRACT(EPOCH FROM next_slot_at - now()), 0) FROM stage_pace WHERE stage = :stage
"""

HOLD_SQL = """
    UPDATE stage_pace
    SET next_slot_at = GREATEST(next_slot_at, now() + make_interval(secs => CAST(:pause_seconds AS double precision))),
        cooldown_until = GREATEST(cooldown_until, now() + make_interval(secs => CAST(:pause_seconds AS double precision))),
        updated_at = now()
    WHERE stage = :stage
"""

RECORD_RESPONSE_TIME_SQL = """
    UPDATE stage_pace
    SET avg_response_ms = CASE
            WHEN avg_response_ms IS NULL THEN CAST(:sample_ms AS integer)
            ELSE round(avg_response_ms * (1 - CAST(:sample_weight AS double precision))
                + CAST(:sample_ms AS integer) * CAST(:sample_weight AS double precision))
        END,
        updated_at = now()
    WHERE stage = :stage
"""


class PaceStoreUnavailable(Exception):
    """The stage_pace table could not be used; the caller paces in memory instead."""


class PaceWaitTooLong(Exception):
    """The next free slot is further away than the caller is willing to wait. No slot
    was reserved."""

    def __init__(self, retry_after_seconds: float, stage: str | None = None) -> None:
        super().__init__(f"Next free slot is {retry_after_seconds:.1f} seconds away")
        self.retry_after_seconds = retry_after_seconds
        self.stage = stage


class ReservedSlot(NamedTuple):
    slot_at: datetime
    wait_seconds: float


def _connect() -> pg8000.native.Connection:
    return pg8000.native.Connection(**_parse_database_url(settings.database_url), timeout=DATABASE_TIMEOUT_SECONDS)


def _milliseconds(seconds: float) -> int:
    return round(seconds * MILLISECONDS_PER_SECOND)


class PaceStateStore:
    """Reads and writes stage_pace rows over one reused connection. Slot reservation
    is a single UPDATE, so concurrent callers in any process each get their own slot."""

    def __init__(
        self,
        connect: Callable[[], object] = _connect,
        clock: Callable[[], float] = time.monotonic,
        response_time_writer: Executor | None = None,
    ) -> None:
        self._connect = connect
        self._clock = clock
        self._lock = threading.Lock()
        self._connection = None
        self._unavailable_until: float | None = None
        self._response_time_writer = response_time_writer or ThreadPoolExecutor(
            max_workers=RESPONSE_TIME_WRITER_THREADS, thread_name_prefix="stage-pace"
        )

    def reserve_slot(self, stage: str, interval_seconds: float, maximum_wait_seconds: float | None) -> ReservedSlot:
        """Reserves the stage's next free slot and returns it with how many seconds away it is.
        With maximum_wait_seconds set, a slot further away is not reserved and
        PaceWaitTooLong carries the current wait instead."""
        slot_parameters = {"stage": stage, "interval_seconds": interval_seconds, "delay_ms": _milliseconds(interval_seconds)}
        if maximum_wait_seconds is None:
            rows = self._run(RESERVE_SLOT_SQL, **slot_parameters)
        else:
            rows = self._run(RESERVE_SLOT_WITHIN_WAIT_SQL, maximum_wait_seconds=maximum_wait_seconds, **slot_parameters)
            if not rows:
                current_wait_rows = self._run(CURRENT_WAIT_SQL, stage=stage)
                if current_wait_rows:
                    ((current_wait_seconds,),) = current_wait_rows
                    raise PaceWaitTooLong(float(current_wait_seconds), stage)
        if not rows:
            self._mark_unavailable()
            logger.warning("Stage pace row for %s is missing, pacing in memory", stage)
            raise PaceStoreUnavailable(stage)
        ((slot_at, wait_seconds),) = rows
        return ReservedSlot(slot_at=slot_at, wait_seconds=max(float(wait_seconds), NO_WAIT_SECONDS))

    def hold(self, stage: str, pause_seconds: float) -> None:
        self._run(HOLD_SQL, stage=stage, pause_seconds=pause_seconds)

    def record_response_time(self, stage: str, elapsed_seconds: float) -> None:
        """Folds one response time into the stage's moving average on a background
        thread, so recording never delays the request that was measured."""
        try:
            self._response_time_writer.submit(self._persist_response_time, stage, _milliseconds(elapsed_seconds))
        except Exception as submit_error:
            logger.warning("Response time for %s was not recorded: %s", stage, type(submit_error).__name__)

    def _persist_response_time(self, stage: str, sample_ms: int) -> None:
        try:
            self._run(
                RECORD_RESPONSE_TIME_SQL, stage=stage, sample_ms=sample_ms, sample_weight=RESPONSE_TIME_SMOOTHING_WEIGHT
            )
        except PaceStoreUnavailable:
            pass

    def _mark_unavailable(self) -> None:
        self._unavailable_until = self._clock() + UNAVAILABLE_RETRY_SECONDS

    def _close_connection(self) -> None:
        connection, self._connection = self._connection, None
        if connection is None:
            return
        try:
            connection.close()
        except Exception:
            pass

    def _run(self, sql: str, **parameters) -> list:
        with self._lock:
            if self._unavailable_until is not None and self._clock() < self._unavailable_until:
                raise PaceStoreUnavailable()
            try:
                if self._connection is None:
                    self._connection = self._connect()
                return self._connection.run(sql, **parameters)
            except Exception as database_error:
                self._close_connection()
                self._mark_unavailable()
                logger.warning(
                    "Stage pace state is unavailable, pacing in memory for %s seconds: %s",
                    UNAVAILABLE_RETRY_SECONDS,
                    type(database_error).__name__,
                )
                raise PaceStoreUnavailable() from database_error


# Every pacer in the process shares this store; None paces everything in memory.
shared_store: PaceStateStore | None = PaceStateStore()
