"""Spike: MusicBrainz release-group search, see spikes/README.md.

Usage: python spikes/musicbrainz_spike.py "<title>" "<artist>"
"""

import sys
from urllib.parse import quote

import httpx
from _shared import USER_AGENT


def search_release_group(title: str, artist: str) -> dict:
    query = f'releasegroup:"{title}" AND artist:"{artist}"'
    url = f"https://musicbrainz.org/ws/2/release-group/?query={quote(query)}&fmt=json&limit=5"
    response = httpx.get(url, headers={"User-Agent": USER_AGENT}, timeout=10.0)
    response.raise_for_status()
    return response.json()


def summarize(data: dict) -> None:
    groups = data.get("release-groups", [])
    print(f"{len(groups)} release-group match(es)")
    for group in groups:
        artist_credit = ", ".join(credit["name"] for credit in group.get("artist-credit", []))
        tags = [tag["name"] for tag in group.get("tags", [])]
        print(
            f"  score={group.get('score')} title={group.get('title')!r} "
            f"artist={artist_credit!r} first-release-date={group.get('first-release-date')} "
            f"primary-type={group.get('primary-type')} tags={tags}"
        )


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: musicbrainz_spike.py <title> <artist>")
        sys.exit(1)
    summarize(search_release_group(sys.argv[1], sys.argv[2]))
