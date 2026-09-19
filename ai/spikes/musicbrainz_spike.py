"""Spike: MusicBrainz release-group search, see spikes/README.md.

Usage: python spikes/musicbrainz_spike.py "<title>" "<artist>"
"""

import sys
import time
from urllib.parse import quote

from _shared import MUSICBRAINZ_DELAY_SECONDS, RETRY_EVENT_COUNTS, USER_AGENT, get_with_backoff

RELEASE_GROUP_SEARCH_LIMIT = 10

SUCCESS_STREAK_BEFORE_EASING = 5  # only relax after a real run of clean calls, not one lucky response
EASE_STEP_SECONDS = 0.1
BACKOFF_MULTIPLIER = 1.5
MAX_DELAY_SECONDS = 10.0


class MusicBrainzRateLimiter:
    """MusicBrainz exposes no quota-usage headers to adapt off, unlike
    Discogs, so this adapts off observed outcomes instead: a retryable
    failure (503, timeout) triggers a multiplicative back-off, the same
    congestion-control idea TCP uses. Recovers additively, a small step at
    a time, back toward the 67%-target baseline on a sustained run of
    clean calls, never below that baseline, since without a real usage
    signal there's no way to confirm going faster than the documented cap
    allows stays safe."""

    def __init__(self, baseline_delay_seconds: float = MUSICBRAINZ_DELAY_SECONDS):
        self.baseline_delay_seconds = baseline_delay_seconds
        self.delay_seconds = baseline_delay_seconds
        self.consecutive_successes = 0

    def wait(self) -> None:
        time.sleep(self.delay_seconds)

    def record_success(self) -> None:
        self.consecutive_successes += 1
        if self.consecutive_successes >= SUCCESS_STREAK_BEFORE_EASING and self.delay_seconds > self.baseline_delay_seconds:
            self.delay_seconds = max(self.baseline_delay_seconds, self.delay_seconds - EASE_STEP_SECONDS)
            self.consecutive_successes = 0

    def record_failure(self) -> None:
        self.consecutive_successes = 0
        self.delay_seconds = min(self.delay_seconds * BACKOFF_MULTIPLIER, MAX_DELAY_SECONDS)


rate_limiter = MusicBrainzRateLimiter()


def _get(url: str) -> dict:
    rate_limiter.wait()
    retry_count_before = sum(RETRY_EVENT_COUNTS.values())
    response = get_with_backoff(url, headers={"User-Agent": USER_AGENT})
    if sum(RETRY_EVENT_COUNTS.values()) > retry_count_before:
        rate_limiter.record_failure()
    else:
        rate_limiter.record_success()
    return response.json()


def search_release_group(title: str, artist: str) -> dict:
    query = f'releasegroup:"{title}" AND artist:"{artist}"'
    url = (
        f"https://musicbrainz.org/ws/2/release-group/?query={quote(query)}"
        f"&fmt=json&limit={RELEASE_GROUP_SEARCH_LIMIT}"
    )
    return _get(url)


def get_artist(artist_id: str) -> dict:
    return _get(f"https://musicbrainz.org/ws/2/artist/{artist_id}?fmt=json")


def select_best_release_group(release_groups: list[dict], prefer_type: str = "Single") -> dict | None:
    """The top-scored result isn't necessarily the original: MusicBrainz can
    return several equally-scored release-groups for the same title (a
    reissue or compilation as a separate group from the original single),
    sometimes with a later or missing first-release-date on whichever one
    happens to sort first. Scans every top-scored candidate, prefers
    prefer_type, and takes the earliest valid date among them.

    prefer_type matters beyond just picking the right date: a same-titled
    single and album can tie in score, defaulting to "Single" for an
    album-level lookup would silently substitute a different work. Callers
    doing an album-level lookup should pass prefer_type="Album"."""
    if not release_groups:
        return None

    top_score = max(release_group.get("score", 0) for release_group in release_groups)
    top_scored_groups = [
        release_group for release_group in release_groups if release_group.get("score", 0) == top_score
    ]

    preferred_type_groups = [
        release_group for release_group in top_scored_groups if release_group.get("primary-type") == prefer_type
    ]
    candidate_groups = preferred_type_groups or top_scored_groups

    dated_groups = [release_group for release_group in candidate_groups if release_group.get("first-release-date")]
    if dated_groups:
        return min(dated_groups, key=lambda release_group: release_group["first-release-date"])
    arbitrary_undated_group = candidate_groups[0]
    return arbitrary_undated_group


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
    _script_path, title, artist = sys.argv
    data = search_release_group(title, artist)
    summarize(data)
    best = select_best_release_group(data.get("release-groups", []))
    if best:
        print(f"\nSelected: first-release-date={best.get('first-release-date')} primary-type={best.get('primary-type')}")
