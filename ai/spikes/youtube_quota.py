"""Persisted daily-quota guard for the YouTube Data API v3.

Unlike MusicBrainz/Discogs/Wikidata, YouTube isn't a per-second or
per-minute rate, it's a daily unit budget (default 10,000 units/day,
different operations cost different amounts, reset at midnight Pacific
time regardless of the caller's own timezone). State persists to a local,
gitignored file so the cap holds across separate script invocations on the
same day, an in-memory counter would reset every run and not actually
protect anything. See spikes/README.md.
"""

import json
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from _shared import RATE_LIMIT_TARGET_UTILIZATION

DAILY_QUOTA_LIMIT_UNITS = 10_000  # default Google Cloud project allocation
DAILY_QUOTA_CAP_UNITS = int(DAILY_QUOTA_LIMIT_UNITS * RATE_LIMIT_TARGET_UTILIZATION)
PACIFIC_TIMEZONE = ZoneInfo("America/Los_Angeles")

STATE_FILE_PATH = Path(__file__).resolve().parent / ".youtube_quota_state.json"


def _today_pacific() -> str:
    return datetime.now(PACIFIC_TIMEZONE).date().isoformat()


def _load_state() -> dict:
    today = _today_pacific()
    if not STATE_FILE_PATH.exists():
        return {"date": today, "units_used": 0}
    state = json.loads(STATE_FILE_PATH.read_text())
    if state.get("date") != today:
        return {"date": today, "units_used": 0}
    return state


def _save_state(state: dict) -> None:
    STATE_FILE_PATH.write_text(json.dumps(state))


def charge(units: int, *, operation: str) -> None:
    """Call before making a YouTube API request that costs `units`. Raises
    rather than silently making the call and finding out from a 403 later
    that the real daily quota ran out, this stops at the target-utilization
    cap with headroom still unspent."""
    state = _load_state()
    projected_units = state["units_used"] + units
    if projected_units > DAILY_QUOTA_CAP_UNITS:
        raise RuntimeError(
            f"YouTube quota guard: {operation} (cost {units}) would bring today's usage to "
            f"{projected_units}/{DAILY_QUOTA_LIMIT_UNITS}, past the {DAILY_QUOTA_CAP_UNITS}-unit "
            f"({RATE_LIMIT_TARGET_UTILIZATION:.0%}) cap. Currently at {state['units_used']} units used today."
        )
    state["units_used"] = projected_units
    _save_state(state)


def remaining_units() -> int:
    return DAILY_QUOTA_CAP_UNITS - _load_state()["units_used"]
