"""Spike: Discogs release search + master lookup, see spikes/README.md.

A release search returns individual pressings/reissues, each with its own
year. Following a result's master_id to /masters/{id} gives the canonical
work-level year instead.

Usage: python spikes/discogs_spike.py "<title>" "<artist>"
"""

import sys
from pathlib import Path

from dotenv import dotenv_values
from _shared import USER_AGENT, get_with_backoff

_env = dotenv_values(Path(__file__).resolve().parent.parent / ".env")


def _auth_header() -> str:
    return f"Discogs key={_env['DISCOGS_CONSUMER_KEY']}, secret={_env['DISCOGS_CONSUMER_SECRET']}"


def search_release(title: str, artist: str) -> dict:
    response = get_with_backoff(
        "https://api.discogs.com/database/search",
        params={"q": f"{artist} {title}", "type": "release"},
        headers={"User-Agent": USER_AGENT, "Authorization": _auth_header()},
    )
    return response.json()


def get_master(master_id: int) -> dict:
    response = get_with_backoff(
        f"https://api.discogs.com/masters/{master_id}",
        headers={"User-Agent": USER_AGENT, "Authorization": _auth_header()},
    )
    return response.json()


def find_master_ids(releases: list[dict], limit: int = 3) -> list[int]:
    """A single track can belong to more than one distinct master, its own
    standalone single release and the album it also appears on, each with
    its own master and its own year (live-tested: "Hey Mama" has a 2015
    single master and is track 10 on "Listen," whose master year is 2014,
    the true original). Trusting whichever master a search result lists
    first picked the single over the earlier album. Returns every distinct
    master_id found among the first several results, in first-seen order,
    not just one, so the caller can check all of them and take the
    earliest valid year."""
    seen: list[int] = []
    for release in releases[:15]:
        master_id = release.get("master_id")
        if master_id and master_id not in seen:
            seen.append(master_id)
        if len(seen) >= limit:
            break
    return seen


def master_year(master: dict) -> int | None:
    """Discogs uses 0, not null, for a master with no known year (live-tested
    on "Titanium"), a naive `.get("year")` would treat that as a real, very
    old date instead of "unknown"."""
    year = master.get("year")
    return year if year else None


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: discogs_spike.py <title> <artist>")
        sys.exit(1)

    title, artist = sys.argv[1], sys.argv[2]
    results = search_release(title, artist)
    releases = results.get("results", [])
    total = results.get("pagination", {}).get("items", len(releases))
    print(f"{len(releases)} release result(s) shown, {total} total")
    for release in releases[:5]:
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
