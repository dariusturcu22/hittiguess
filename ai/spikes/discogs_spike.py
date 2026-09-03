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


def find_master_id(releases: list[dict]) -> int | None:
    """The very first search result doesn't always have a master_id, an
    unofficial or one-off release can lack one entirely (the niche vaporwave
    test song's top result had none). Scans the first several results
    instead of trusting releases[0]."""
    for release in releases[:10]:
        if release.get("master_id"):
            return release["master_id"]
    return None


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

    master_ids = sorted({r["master_id"] for r in releases if r.get("master_id")})
    print(f"\n{len(master_ids)} distinct master(s) among the shown results")
    for master_id in master_ids[:3]:
        master = get_master(master_id)
        print(
            f"  master {master_id}: year={master.get('year')} title={master.get('title')!r} "
            f"genres={master.get('genres')} styles={master.get('styles')}"
        )
