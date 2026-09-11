import time
from urllib.parse import quote

from app.metadata.sources.http_retry import get_with_backoff
from app.metadata.sources.util import METADATA_SOURCE_USER_AGENT, escape_lucene

REQUEST_TIMEOUT_SECONDS = 10.0
RELEASE_GROUP_SEARCH_LIMIT = 10
CANDIDATES_TO_KEEP_PER_QUERY = 5

MUSICBRAINZ_LIMIT_PER_MINUTE = 60  # documented hard limit is 1 request/second, per IP
RATE_LIMIT_TARGET_UTILIZATION = 0.67  # hard cap, stay comfortably inside the documented ceiling
BASELINE_DELAY_SECONDS = 60 / (MUSICBRAINZ_LIMIT_PER_MINUTE * RATE_LIMIT_TARGET_UTILIZATION)
SUCCESS_STREAK_BEFORE_EASING = 5  # only relax after a real run of clean calls, not one lucky response
EASE_STEP_SECONDS = 0.1
BACKOFF_MULTIPLIER = 1.5
MAX_DELAY_SECONDS = 10.0


class _AdaptiveRateLimiter:
    """MusicBrainz exposes no quota-usage headers to adapt off, unlike
    Discogs, so this adapts off observed outcomes instead: a retryable
    failure (429, 503, a transport error) triggers a multiplicative
    back-off, the same congestion-control idea TCP uses. Recovers
    additively, a small step at a time, back toward the baseline delay on a
    sustained run of clean calls, never below that baseline, since without
    a real usage signal there's no way to confirm going faster than the
    documented cap allows stays safe."""

    def __init__(self, baseline_delay_seconds: float = BASELINE_DELAY_SECONDS):
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


_rate_limiter = _AdaptiveRateLimiter()


def _get(url: str) -> dict:
    _rate_limiter.wait()
    response = get_with_backoff(
        url,
        headers={"User-Agent": METADATA_SOURCE_USER_AGENT},
        timeout=REQUEST_TIMEOUT_SECONDS,
        on_retry=_rate_limiter.record_failure,
    )
    _rate_limiter.record_success()
    return response.json()


def search_release_group(title: str, artist: str) -> dict:
    query = f'releasegroup:"{escape_lucene(title)}" AND artist:"{escape_lucene(artist)}"'
    url = (
        f"https://musicbrainz.org/ws/2/release-group/?query={quote(query)}"
        f"&fmt=json&limit={RELEASE_GROUP_SEARCH_LIMIT}"
    )
    return _get(url)


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
    doing an album-level lookup pass prefer_type="Album"."""
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
    return candidate_groups[0]


def _candidates_from_groups(release_groups: list[dict], query_label: str) -> list[dict]:
    candidates = []
    for release_group in release_groups[:CANDIDATES_TO_KEEP_PER_QUERY]:
        artist_credit = ", ".join(credit["name"] for credit in release_group.get("artist-credit", []))
        candidates.append(
            {
                "query": query_label,
                "title": release_group.get("title"),
                "artist": artist_credit,
                "date": release_group.get("first-release-date"),
                "type": release_group.get("primary-type"),
                "score": release_group.get("score"),
            }
        )
    return candidates


def search(title: str, artist: str, album: str | None = None) -> list[dict]:
    """Queries the submitted track and, when given, its parent album,
    release-group by release-group, so the caller can take the earliest
    valid year across both rather than trusting whichever query's top
    result happens to be a reissue (see docs/DECISIONS.md's 2026-09
    "Metadata pipeline call/reconcile shape" entry). Returns every
    candidate found, not just the selected best one, so downstream
    reconciliation can see the full picture a single pick would hide."""
    try:
        candidates: list[dict] = []

        track_groups = search_release_group(title, artist).get("release-groups", [])
        candidates += _candidates_from_groups(track_groups, "track")

        if album:
            album_groups = search_release_group(album, artist).get("release-groups", [])
            candidates += _candidates_from_groups(album_groups, "album")

        return candidates
    except Exception:
        return []
