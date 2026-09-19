import time

import httpx

from app.config import settings
from app.metadata.sources.http_retry import get_with_backoff
from app.metadata.sources.util import METADATA_SOURCE_USER_AGENT
from app.observability.error_reporting import report_source_failure

SOURCE_NAME = "discogs"
SEARCH_URL = "https://api.discogs.com/database/search"
MASTER_URL_TEMPLATE = "https://api.discogs.com/masters/{master_id}"
REQUEST_TIMEOUT_SECONDS = 10.0

MAX_DISTINCT_MASTERS = 3
MAX_RELEASES_TO_SCAN_FOR_MASTERS = 15

DISCOGS_DOCUMENTED_LIMIT_PER_MINUTE = 60  # authenticated tier
RATE_LIMIT_TARGET_UTILIZATION = 0.67  # stay comfortably inside the documented ceiling
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
    configured utilization of the documented 60/minute ceiling, then adapts:
    slows down and takes a one-time cooldown if a response shows usage
    crossed the target band, eases back toward the starting delay when
    there's headroom to spare."""

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
            time.sleep(BREACH_COOLDOWN_SECONDS)
        elif utilization < self.target_utilization - UTILIZATION_ADJUSTMENT_BAND:
            self.delay_seconds = max(self.delay_seconds * DELAY_DECREASE_MULTIPLIER, MIN_DELAY_SECONDS)


_rate_limiter = DiscogsRateLimiter()


def _auth_header() -> str:
    return f"Discogs key={settings.discogs_consumer_key}, secret={settings.discogs_consumer_secret}"


def _get(url: str, params: dict | None = None) -> dict:
    _rate_limiter.wait()
    response = get_with_backoff(
        url,
        params=params,
        headers={"User-Agent": METADATA_SOURCE_USER_AGENT, "Authorization": _auth_header()},
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    _rate_limiter.record_response(response)
    return response.json()


def search_release(title: str, artist: str) -> dict:
    return _get(SEARCH_URL, params={"q": f"{artist} {title}", "type": "release"})


def get_master(master_id: int) -> dict:
    return _get(MASTER_URL_TEMPLATE.format(master_id=master_id))


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
    missing field, so find_master_ids' truthy check correctly excludes it)
    still often carries its own `year` field directly in the search result,
    a real, if less authoritative, signal that would otherwise be silently
    discarded entirely, a niche release can have every one of its
    correct-year appearances lack a master while only an unrelated release
    happens to have one. Returns title/year pairs for releases
    find_master_ids would otherwise drop, for the caller to merge in as
    lower-confidence candidates alongside the master-based ones. A search
    result's own `year` field comes back as a string, unlike a master
    resource's `year`, which is an int; cast here so callers can compare or
    sort masterless years against master-based ones without a
    string-versus-int mismatch."""
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


def _candidates_for_query(title: str, artist: str, query_label: str) -> list[dict]:
    releases = search_release(title, artist).get("results", [])

    candidates = []
    for master_id in find_master_ids(releases):
        master = get_master(master_id)
        year = master_year(master)
        if year is not None:
            candidates.append({"query": query_label, "title": master.get("title"), "year": year})

    for masterless_candidate in masterless_release_years(releases):
        candidates.append(
            {"query": query_label, "title": masterless_candidate["title"], "year": masterless_candidate["year"]}
        )

    return candidates


def search(title: str, artist: str, album: str | None = None) -> list[dict]:
    """Queries the submitted track and, when given, its parent album, so
    the caller can take the earliest valid year across both rather than
    trusting whichever query's top result happens to be a reissue (see
    docs/DECISIONS.md's 2026-09 "Metadata pipeline call/reconcile shape"
    entry). Returns every candidate found, both master-based and
    masterless, not just a single selected best one, so downstream
    reconciliation can see the full picture a single pick would hide."""
    try:
        candidates = _candidates_for_query(title, artist, "track")

        if album:
            candidates += _candidates_for_query(album, artist, "album")

        return candidates
    except Exception as discogs_error:
        report_source_failure(SOURCE_NAME, discogs_error, title, artist)
        return []
