"""Spike: MusicBrainz release-group search, see spikes/README.md.

Usage: python spikes/musicbrainz_spike.py "<title>" "<artist>"
"""

import sys
from urllib.parse import quote

from _shared import USER_AGENT, get_with_backoff


def search_release_group(title: str, artist: str) -> dict:
    query = f'releasegroup:"{title}" AND artist:"{artist}"'
    url = f"https://musicbrainz.org/ws/2/release-group/?query={quote(query)}&fmt=json&limit=10"
    response = get_with_backoff(url, headers={"User-Agent": USER_AGENT})
    return response.json()


def get_artist(artist_id: str) -> dict:
    url = f"https://musicbrainz.org/ws/2/artist/{artist_id}?fmt=json"
    response = get_with_backoff(url, headers={"User-Agent": USER_AGENT})
    return response.json()


def select_best_release_group(groups: list[dict], prefer_type: str = "Single") -> dict | None:
    """The top-scored result isn't necessarily the original: MusicBrainz can
    return several score=100 release-groups for the same title (a reissue or
    compilation as a separate group from the original single), sometimes with
    a later or missing first-release-date on the one that happens to sort
    first. Scans every top-scored candidate, prefers prefer_type, and takes
    the earliest valid date among them.

    prefer_type matters beyond just picking the right date: an album query
    for "Thriller" can tie in score with the unrelated same-titled "Thriller"
    single, defaulting to "Single" there would silently substitute a
    different work. Callers doing an album-level lookup should pass
    prefer_type="Album"."""
    if not groups:
        return None

    top_score = max(g.get("score", 0) for g in groups)
    candidates = [g for g in groups if g.get("score", 0) == top_score]

    preferred = [g for g in candidates if g.get("primary-type") == prefer_type]
    pool = preferred or candidates

    dated = [g for g in pool if g.get("first-release-date")]
    if dated:
        return min(dated, key=lambda g: g["first-release-date"])
    return pool[0]


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
    data = search_release_group(sys.argv[1], sys.argv[2])
    summarize(data)
    best = select_best_release_group(data.get("release-groups", []))
    if best:
        print(f"\nSelected: first-release-date={best.get('first-release-date')} primary-type={best.get('primary-type')}")
