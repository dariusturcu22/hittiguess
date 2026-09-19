"""Spike: Discogs release search + master lookup, see spikes/README.md.

A release search returns individual pressings/reissues, each with its own
year. Following a result's master_id to /masters/{id} gives the canonical
work-level year instead.

Usage: python spikes/discogs_spike.py "<title>" "<artist>"
"""

import sys
import time
from pathlib import Path

import httpx
from dotenv import dotenv_values
from _shared import RATE_LIMIT_TARGET_UTILIZATION, USER_AGENT, get_with_backoff

_env = dotenv_values(Path(__file__).resolve().parent.parent / ".env")

MAX_DISTINCT_MASTERS = 3
MAX_RELEASES_TO_SCAN_FOR_MASTERS = 15
DISPLAY_RESULT_COUNT = 5

DISCOGS_DOCUMENTED_LIMIT_PER_MINUTE = 60  # authenticated tier, see spikes/README.md
UTILIZATION_ADJUSTMENT_BAND = 0.05  # don't react to noise within +/-5% of the target
DELAY_INCREASE_MULTIPLIER = 1.5
DELAY_DECREASE_MULTIPLIER = 0.9
MIN_DELAY_SECONDS = 60 / DISCOGS_DOCUMENTED_LIMIT_PER_MINUTE  # never faster than the documented ceiling implies
MAX_DELAY_SECONDS = 10.0
BREACH_COOLDOWN_SECONDS = 8.0


class DiscogsRateLimiter:
    """Paces Discogs calls off the real X-Discogs-Ratelimit-* response
    headers instead of a fixed guess, since Discogs is the one source that
    actually reports live quota usage. Starts at a delay targeting the
    requested utilization of the documented 60/minute ceiling, then adapts:
    slows down and takes a one-time cooldown if a response shows usage
    crossed the target band (a burst of retries can do this even when the
    intended steady-state rate is compliant), eases back toward the
    starting delay when there's headroom to spare."""

    def __init__(self, target_utilization: float = RATE_LIMIT_TARGET_UTILIZATION):
        self.target_utilization = target_utilization
        self.starting_delay_seconds = 60 / (DISCOGS_DOCUMENTED_LIMIT_PER_MINUTE * target_utilization)
        self.delay_seconds = self.starting_delay_seconds
        self.last_observed_utilization: float | None = None

    def wait(self) -> None:
        time.sleep(self.delay_seconds)

    def record_response(self, response: httpx.Response) -> None:
        limit = response.headers.get("X-Discogs-Ratelimit")
        remaining = response.headers.get("X-Discogs-Ratelimit-Remaining")
        if limit is None or remaining is None:
            return
        limit, remaining = int(limit), int(remaining)
        if limit == 0:
            return
        utilization = (limit - remaining) / limit
        self.last_observed_utilization = utilization

        if utilization > self.target_utilization + UTILIZATION_ADJUSTMENT_BAND:
            self.delay_seconds = min(self.delay_seconds * DELAY_INCREASE_MULTIPLIER, MAX_DELAY_SECONDS)
            print(
                f"  [Discogs quota at {utilization:.0%}, above the {self.target_utilization:.0%} target, "
                f"cooling off {BREACH_COOLDOWN_SECONDS:.0f}s and slowing to {self.delay_seconds:.2f}s/call]"
            )
            time.sleep(BREACH_COOLDOWN_SECONDS)
        elif utilization < self.target_utilization - UTILIZATION_ADJUSTMENT_BAND:
            self.delay_seconds = max(self.delay_seconds * DELAY_DECREASE_MULTIPLIER, MIN_DELAY_SECONDS)


rate_limiter = DiscogsRateLimiter()


def _auth_header() -> str:
    return f"Discogs key={_env['DISCOGS_CONSUMER_KEY']}, secret={_env['DISCOGS_CONSUMER_SECRET']}"


def search_release(title: str, artist: str) -> dict:
    rate_limiter.wait()
    response = get_with_backoff(
        "https://api.discogs.com/database/search",
        params={"q": f"{artist} {title}", "type": "release"},
        headers={"User-Agent": USER_AGENT, "Authorization": _auth_header()},
    )
    rate_limiter.record_response(response)
    return response.json()


def get_master(master_id: int) -> dict:
    rate_limiter.wait()
    response = get_with_backoff(
        f"https://api.discogs.com/masters/{master_id}",
        headers={"User-Agent": USER_AGENT, "Authorization": _auth_header()},
    )
    rate_limiter.record_response(response)
    return response.json()


def find_master_ids(releases: list[dict], max_masters: int = MAX_DISTINCT_MASTERS) -> list[int]:
    """A single track can belong to more than one distinct master, its own
    standalone single release and the album it also appears on, each with
    its own master and its own year. Trusting whichever master a search
    result lists first can pick a later single over an earlier album.
    Returns every distinct master_id found among the first several results,
    in first-seen order, not just one, so the caller can check all of them
    and take the earliest valid year."""
    seen_master_ids: list[int] = []
    for release in releases[:MAX_RELEASES_TO_SCAN_FOR_MASTERS]:
        master_id = release.get("master_id")
        if master_id and master_id not in seen_master_ids:
            seen_master_ids.append(master_id)
        if len(seen_master_ids) >= max_masters:
            break
    return seen_master_ids


def master_year(master: dict) -> int | None:
    """Discogs uses 0, not null, for a master with no known year, a naive
    `.get("year")` would treat that as a real, very old date instead of
    "unknown"."""
    year = master.get("year")
    return year if year else None


def masterless_release_years(
    releases: list[dict], max_releases: int = MAX_RELEASES_TO_SCAN_FOR_MASTERS
) -> list[dict]:
    """A release with no master_id (Discogs reports this as 0, not a
    missing field, so find_master_ids' truthy check correctly excludes
    it) still often carries its own `year` field directly in the search
    result, a real, if less authoritative, signal that would otherwise be
    silently discarded entirely, a niche release can have every one of
    its correct-year appearances lack a master while only an unrelated
    release happens to have one. Returns title/year pairs for releases
    find_master_ids would otherwise drop, for the caller to merge in as
    lower-confidence candidates alongside the master-based ones. A
    search result's own `year` field comes back as a string ("2011"),
    unlike a master resource's `year`, which is an int; cast here so
    callers can compare or sort masterless years against master-based
    ones without a str-vs-int mismatch."""
    candidates = []
    for release in releases[:max_releases]:
        if release.get("master_id") or not release.get("year"):
            continue
        try:
            year = int(release["year"])
        except (TypeError, ValueError):
            continue
        candidates.append({"title": release.get("title"), "year": year})
    return candidates


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: discogs_spike.py <title> <artist>")
        sys.exit(1)

    _script_path, title, artist = sys.argv
    results = search_release(title, artist)
    releases = results.get("results", [])
    total = results.get("pagination", {}).get("items", len(releases))
    print(f"{len(releases)} release result(s) shown, {total} total")
    for release in releases[:DISPLAY_RESULT_COUNT]:
        print(
            f"  year={release.get('year')} title={release.get('title')!r} "
            f"master_id={release.get('master_id')} country={release.get('country')}"
        )

    master_ids = find_master_ids(releases)
    print(f"\n{len(master_ids)} distinct master(s) among the shown results")
    for master_id in master_ids:
        master = get_master(master_id)
        print(
            f"  master {master_id}: year={master_year(master)} title={master.get('title')!r} "
            f"genres={master.get('genres')} styles={master.get('styles')}"
        )
