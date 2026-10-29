import re
import time
from urllib.parse import quote

from app.metadata.sources.http_retry import get_with_backoff
from app.metadata.sources.pace_state import STAGE_MUSICBRAINZ
from app.metadata.sources.pacing import PersistedRequestPacer
from app.metadata.sources.util import METADATA_SOURCE_USER_AGENT, escape_lucene, normalize_name
from app.observability.error_reporting import report_source_failure

SOURCE_NAME = "musicbrainz"

REQUEST_TIMEOUT_SECONDS = 10.0
RELEASE_GROUP_SEARCH_LIMIT = 10
RECORDING_SEARCH_LIMIT = 50
RECORDING_MIN_SCORE = 90
CANDIDATES_TO_KEEP_PER_QUERY = 5

ALBUM_PRIMARY_TYPES = frozenset({"Album", "EP"})
EXCLUDED_RELEASE_SECONDARY_TYPES = frozenset(
    {"Compilation", "Live", "Remix", "DJ-mix", "Mixtape/Street", "Demo"}
)
EXCLUDED_RECORDING_DISAMBIGUATION_TERMS = (
    "live",
    "remix",
    "acoustic",
    "instrumental",
    "karaoke",
    "demo",
    "edit",
)
TITLE_VERSION_MARKERS = frozenset(
    {
        "album",
        "acoustic",
        "demo",
        "edit",
        "extended",
        "feat",
        "from",
        "ft",
        "instrumental",
        "live",
        "mix",
        "ost",
        "radio",
        "remix",
        "remixes",
        "single",
        "soundtrack",
        "version",
    }
)

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
        self._pacer = PersistedRequestPacer(STAGE_MUSICBRAINZ)

    def wait(self) -> None:
        self._pacer.wait(self.delay_seconds)

    def record_success(self) -> None:
        self.consecutive_successes += 1
        if self.consecutive_successes >= SUCCESS_STREAK_BEFORE_EASING and self.delay_seconds > self.baseline_delay_seconds:
            self.delay_seconds = max(self.baseline_delay_seconds, self.delay_seconds - EASE_STEP_SECONDS)
            self.consecutive_successes = 0

    def record_response_time(self, elapsed_seconds: float) -> None:
        self._pacer.record_response_time(elapsed_seconds)

    def record_failure(self) -> None:
        self.consecutive_successes = 0
        self.delay_seconds = min(self.delay_seconds * BACKOFF_MULTIPLIER, MAX_DELAY_SECONDS)
        self._pacer.hold(self.delay_seconds)


_rate_limiter = _AdaptiveRateLimiter()


def _get(url: str) -> dict:
    _rate_limiter.wait()
    request_started_at = time.monotonic()
    response = get_with_backoff(
        url,
        headers={"User-Agent": METADATA_SOURCE_USER_AGENT},
        timeout=REQUEST_TIMEOUT_SECONDS,
        on_retry=_rate_limiter.record_failure,
    )
    _rate_limiter.record_response_time(time.monotonic() - request_started_at)
    _rate_limiter.record_success()
    return response.json()


def _artist_credit_names(artist_credit: list[dict]) -> list[str]:
    names = []
    for credit in artist_credit:
        credit_name = credit.get("name")
        if credit_name:
            names.append(credit_name)
        artist_name = (credit.get("artist") or {}).get("name")
        if artist_name:
            names.append(artist_name)
    return names


def _artist_credit_display(artist_credit: list[dict]) -> str:
    return ", ".join(credit["name"] for credit in artist_credit if credit.get("name"))


def _artist_credit_matches(artist_credit: list[dict], main_artists: list[str]) -> bool:
    normalized_credit = " ".join(normalize_name(name) for name in _artist_credit_names(artist_credit))
    if not normalized_credit:
        return False
    for artist in main_artists:
        normalized_artist = normalize_name(artist)
        if normalized_artist and re.search(rf"(?<!\w){re.escape(normalized_artist)}(?!\w)", normalized_credit):
            return True
    return False


def _top_scored_release_groups(release_groups: list[dict]) -> list[dict]:
    if not release_groups:
        return []
    top_score = max(release_group.get("score", 0) for release_group in release_groups)
    return [
        release_group for release_group in release_groups if release_group.get("score", 0) == top_score
    ]


def _is_original_track_release_group(release_group: dict) -> bool:
    secondary_types = set(release_group.get("secondary-types") or [])
    return not secondary_types.intersection(EXCLUDED_RELEASE_SECONDARY_TYPES)


def _title_matches(candidate_title: str, song_title: str) -> bool:
    candidate = normalize_name(candidate_title)
    song = normalize_name(song_title)
    if not candidate or not song:
        return False
    if candidate == song:
        return True
    title_prefix = f"{song} "
    if not candidate.startswith(title_prefix):
        return False
    suffix_words = candidate[len(title_prefix) :].split()
    if not suffix_words:
        return False
    first_suffix_word = suffix_words[0]
    return first_suffix_word in TITLE_VERSION_MARKERS


def _recording_matches(recording: dict, title: str, main_artists: list[str]) -> bool:
    if recording.get("score", 0) < RECORDING_MIN_SCORE:
        return False
    if not _artist_credit_matches(recording.get("artist-credit", []), main_artists):
        return False
    disambiguation = str(recording.get("disambiguation") or "").lower()
    if any(term in disambiguation for term in EXCLUDED_RECORDING_DISAMBIGUATION_TERMS):
        return False
    return _title_matches(recording.get("title", ""), title)


def search_release_group(title: str, artist: str) -> dict:
    query = f'releasegroup:"{escape_lucene(title)}" AND artist:"{escape_lucene(artist)}"'
    url = (
        f"https://musicbrainz.org/ws/2/release-group/?query={quote(query)}"
        f"&fmt=json&limit={RELEASE_GROUP_SEARCH_LIMIT}"
    )
    return _get(url)


def search_recordings(title: str, artist: str) -> dict:
    query = f'recording:"{escape_lucene(title)}" AND artist:"{escape_lucene(artist)}"'
    url = (
        f"https://musicbrainz.org/ws/2/recording/?query={quote(query)}"
        f"&fmt=json&limit={RECORDING_SEARCH_LIMIT}"
    )
    return _get(url)


def select_best_release_group(release_groups: list[dict], prefer_type: str = "Single") -> dict | None:
    """The top-scored result isn't necessarily the original: MusicBrainz can
    return several equally-scored release-groups for the same title (a
    reissue or compilation as a separate group from the original single),
    sometimes with a later or missing first-release-date on whichever one
    happens to sort first. Scans every top-scored candidate, prefers
    prefer_type, and takes the earliest valid date among them."""
    top_scored_groups = _top_scored_release_groups(release_groups)
    if not top_scored_groups:
        return None

    preferred_type_groups = [
        release_group for release_group in top_scored_groups if release_group.get("primary-type") == prefer_type
    ]
    candidate_groups = preferred_type_groups or top_scored_groups

    dated_groups = [release_group for release_group in candidate_groups if release_group.get("first-release-date")]
    if dated_groups:
        return min(dated_groups, key=lambda release_group: release_group["first-release-date"])
    first_candidate = candidate_groups[0]
    return first_candidate


def _candidates_from_groups(release_groups: list[dict], query_label: str) -> list[dict]:
    candidates = []
    for release_group in release_groups:
        candidates.append(
            {
                "query": query_label,
                "title": release_group.get("title"),
                "artist": _artist_credit_display(release_group.get("artist-credit", [])),
                "date": release_group.get("first-release-date"),
                "type": release_group.get("primary-type"),
                "score": release_group.get("score"),
            }
        )
    return candidates


def _track_candidates(title: str, main_artists: list[str]) -> list[dict]:
    """The track's own release group is the top-scored result among the
    artist-matched original releases. Remix, live, and compilation groups
    describe a different release, so they cannot set the track's year even
    when their search score is higher."""
    if not main_artists:
        return []
    first_main_artist = main_artists[0]
    release_groups = search_release_group(title, first_main_artist).get("release-groups", [])
    artist_matched_groups = [
        release_group
        for release_group in release_groups
        if _artist_credit_matches(release_group.get("artist-credit", []), main_artists)
    ]
    original_groups = [
        release_group
        for release_group in artist_matched_groups
        if _is_original_track_release_group(release_group)
    ]
    return _candidates_from_groups(_top_scored_release_groups(original_groups), "track")


def _album_candidates(title: str, main_artists: list[str]) -> list[dict]:
    """The parent album is found through a recording search, because the
    YouTube identification supplies no album name. A release group's own
    first-release-date can predate the track when the track was added to a
    later edition, so the candidate date is the earliest release date among
    the releases that contain this recording. Soundtrack release groups are
    kept because the recording has already been matched to one of the song's
    artists, which is the condition the soundtrack exception requires."""
    if not main_artists:
        return []
    first_main_artist = main_artists[0]
    recordings = search_recordings(title, first_main_artist).get("recordings", [])
    candidates_by_release_group: dict[str, dict] = {}

    for recording in recordings:
        if not _recording_matches(recording, title, main_artists):
            continue
        recording_artist = _artist_credit_display(recording.get("artist-credit", []))
        for release in recording.get("releases", []):
            release_group = release.get("release-group") or {}
            if release.get("status") != "Official":
                continue
            primary_type = release_group.get("primary-type")
            if primary_type not in ALBUM_PRIMARY_TYPES:
                continue
            secondary_types = set(release_group.get("secondary-types") or [])
            if secondary_types.intersection(EXCLUDED_RELEASE_SECONDARY_TYPES):
                continue
            release_date = release.get("date")
            if not release_date:
                continue

            release_group_id = release_group.get("id") or f"{release_group.get('title')}|{primary_type}"
            existing_candidate = candidates_by_release_group.get(release_group_id)
            if existing_candidate is None:
                candidates_by_release_group[release_group_id] = {
                    "query": "album",
                    "title": release_group.get("title") or release.get("title"),
                    "artist": _artist_credit_display(release_group.get("artist-credit", [])) or recording_artist,
                    "date": release_date,
                    "type": primary_type,
                    "score": recording.get("score"),
                }
            elif release_date < existing_candidate["date"]:
                existing_candidate["date"] = release_date

    candidates = sorted(candidates_by_release_group.values(), key=lambda candidate: candidate["date"])
    return candidates[:CANDIDATES_TO_KEEP_PER_QUERY]


def search_or_raise(title: str, main_artists: list[str]) -> list[dict]:
    """Queries the track through the release-group search with the first main
    artist, then finds its parent albums through the recording search. Returns
    every candidate found, not just the selected best one, so downstream
    reconciliation can see the full picture a single pick would hide. A failed
    request raises."""
    if not main_artists:
        return []
    return _track_candidates(title, main_artists) + _album_candidates(title, main_artists)


def search(title: str, main_artists: list[str]) -> list[dict]:
    """search_or_raise, reporting a failure and returning nothing instead of raising."""
    try:
        return search_or_raise(title, main_artists)
    except Exception as musicbrainz_error:
        report_source_failure(SOURCE_NAME, musicbrainz_error, title, " & ".join(main_artists))
        return []
