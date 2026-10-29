import re
import time

import httpx

from app.config import settings
from app.metadata.sources.http_retry import get_with_backoff
from app.metadata.sources.pace_state import STAGE_DISCOGS
from app.metadata.sources.pacing import PersistedRequestPacer
from app.metadata.sources.util import METADATA_SOURCE_USER_AGENT, normalize_name, to_artist_list
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

EXCLUDED_DISCOGS_FORMATS = frozenset({"compilation", "unofficial release"})
TITLE_SPLIT_MAX_PARTS = 1
FIRST_SPLIT_PART_INDEX = 0


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
        self._pacer = PersistedRequestPacer(STAGE_DISCOGS)

    def wait(self) -> None:
        self._pacer.wait(self.delay_seconds)

    def record_response_time(self, elapsed_seconds: float) -> None:
        self._pacer.record_response_time(elapsed_seconds)

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
            self._pacer.hold(BREACH_COOLDOWN_SECONDS)
        elif utilization < self.target_utilization - UTILIZATION_ADJUSTMENT_BAND:
            self.delay_seconds = max(self.delay_seconds * DELAY_DECREASE_MULTIPLIER, MIN_DELAY_SECONDS)


_rate_limiter = DiscogsRateLimiter()


def _auth_header() -> str:
    return f"Discogs key={settings.discogs_consumer_key}, secret={settings.discogs_consumer_secret}"


def _get(url: str, params: dict | None = None) -> dict:
    _rate_limiter.wait()
    request_started_at = time.monotonic()
    response = get_with_backoff(
        url,
        params=params,
        headers={"User-Agent": METADATA_SOURCE_USER_AGENT, "Authorization": _auth_header()},
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    _rate_limiter.record_response_time(time.monotonic() - request_started_at)
    _rate_limiter.record_response(response)
    return response.json()


def search_release(title: str, main_artists: list[str], query_label: str = "track") -> dict:
    main_artists = to_artist_list(main_artists)
    first_main_artist = main_artists[FIRST_SPLIT_PART_INDEX] if main_artists else ""
    parameters = {"artist": first_main_artist, "type": "release"}
    if query_label == "album":
        parameters["release_title"] = title
    else:
        parameters["track"] = title
    return _get(SEARCH_URL, params=parameters)


def get_master(master_id: int) -> dict:
    return _get(MASTER_URL_TEMPLATE.format(master_id=master_id))


def _matches_discogs_artist(title: str, main_artists: list[str]) -> bool:
    if " - " not in title:
        return False
    split_parts = title.split(" - ", 1)
    prefix = normalize_name(split_parts[0])
    if not prefix:
        return False
    for artist in main_artists:
        normalized_artist = normalize_name(artist)
        if not normalized_artist:
            continue
        if re.search(rf"^{re.escape(normalized_artist)}(?:\b|$)", prefix):
            return True
    return False


def _is_valid_release(release: dict, main_artists: list[str]) -> bool:
    title = release.get("title") or ""
    if title.lower().startswith("various"):
        return False

    formats = [str(format_name).lower() for format_name in release.get("format", [])]
    if any(excluded_format in formats for excluded_format in EXCLUDED_DISCOGS_FORMATS):
        return False

    return _matches_discogs_artist(title, main_artists)


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
    """A release with no master_id still often carries its own `year` field directly
    in the search result. Masterless releases are collected only as lower-confidence
    fallback candidates when no master-based candidate exists for the song."""
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


def _query_candidates(
    title: str, main_artists: list[str], query_label: str
) -> tuple[list[dict], list[dict]]:
    raw_releases = search_release(title, main_artists, query_label).get("results", [])
    releases = [release for release in raw_releases if _is_valid_release(release, main_artists)]

    master_candidates = []
    for master_id in find_master_ids(releases):
        master = get_master(master_id)
        year = master_year(master)
        if year is not None:
            master_candidates.append(
                {"query": query_label, "title": master.get("title"), "year": year}
            )

    masterless_candidates = [
        {"query": query_label, "title": candidate["title"], "year": candidate["year"]}
        for candidate in masterless_release_years(releases)
    ]
    return master_candidates, masterless_candidates


def search_or_raise(
    title: str, main_artists: list[str], album: str | None = None
) -> list[dict]:
    """Queries the submitted track and, when given, its parent album using
    structured search parameters. Releases are filtered to match the main
    artists and drop compilations and unofficial releases. Masterless release
    years are kept only when no master-based candidate exists for the song.
    A failed request raises."""
    main_artists = to_artist_list(main_artists)
    track_masters, track_masterless = _query_candidates(title, main_artists, "track")
    album_masters, album_masterless = [], []
    if album:
        album_masters, album_masterless = _query_candidates(album, main_artists, "album")

    all_masters = track_masters + album_masters
    if all_masters:
        return all_masters

    return track_masterless + album_masterless


def search(
    title: str, main_artists: list[str], album: str | None = None
) -> list[dict]:
    """search_or_raise, reporting a failure and returning nothing instead of raising."""
    try:
        return search_or_raise(title, main_artists, album)
    except Exception as discogs_error:
        artist_display = " & ".join(to_artist_list(main_artists))
        report_source_failure(SOURCE_NAME, discogs_error, title, artist_display)
        return []
