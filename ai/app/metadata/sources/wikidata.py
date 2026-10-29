import re
import time
import unicodedata
from functools import lru_cache

import httpx

from app.config import settings
from app.metadata.sources.http_retry import get_with_backoff
from app.metadata.sources.pace_state import STAGE_WIKIDATA, PaceWaitTooLong
from app.metadata.sources.pacing import PersistedRequestPacer
from app.metadata.sources.mediawiki_auth import build_authenticated_client
from app.metadata.sources.util import METADATA_SOURCE_USER_AGENT, normalize_name, to_artist_list
from app.observability.error_reporting import report_source_failure

SOURCE_NAME = "wikidata"
API_URL = "https://www.wikidata.org/w/api.php"
REQUEST_TIMEOUT_SECONDS = 10.0
DEFAULT_SEARCH_LIMIT = 20  # a common title can bury the real song many results down a narrow window
MAX_WIKIDATA_CANDIDATE_ATTEMPTS = 3

PUBLICATION_DATE_PROPERTY = "P577"
PART_OF_PROPERTY = "P361"
PERFORMER_PROPERTY = "P175"

# Confirmed against mediawiki.org/wiki/Wikimedia_APIs/Rate_limits: 10/min applies to
# requests with no identifying characteristics beyond IP; any logged-in account, no
# approval needed, gets 200/min.
ANONYMOUS_LIMIT_PER_MINUTE = 10
AUTHENTICATED_LIMIT_PER_MINUTE = 200
RATE_LIMIT_TARGET_UTILIZATION = 0.67

_FALLBACK_KEYWORDS = ("song", "single")
_EXCLUDED_KEYWORDS = ("album", "ep", "record", "track", "recording")


def _description_matches_any_artist(description: str, main_artists: list[str]) -> bool:
    normalized_description = normalize_name(description)
    if not normalized_description:
        return False
    for artist in main_artists:
        normalized_artist = normalize_name(artist)
        if normalized_artist and re.search(rf"(?<!\w){re.escape(normalized_artist)}(?!\w)", normalized_description):
            return True
    return False


def _description_matches_fallback(description: str, main_artists: list[str]) -> bool:
    normalized_description = normalize_name(description)
    if not normalized_description:
        return False

    has_song_or_single = any(
        re.search(rf"\b{re.escape(keyword)}\b", normalized_description)
        for keyword in _FALLBACK_KEYWORDS
    )
    if not has_song_or_single:
        return False

    has_excluded_keyword = any(
        re.search(rf"\b{re.escape(keyword)}\b", normalized_description)
        for keyword in _EXCLUDED_KEYWORDS
    )
    if has_excluded_keyword:
        return False

    by_matches = re.findall(r"\bby\s+(.+)", normalized_description)
    if by_matches:
        first_by_clause = by_matches[0]
        names_main_artist = any(
            normalize_name(artist) in first_by_clause
            for artist in main_artists
            if normalize_name(artist)
        )
        if not names_main_artist:
            return False

    return True


def find_matching_candidates(matches: list[dict], artist: str | list[str]) -> list[dict]:
    main_artists = to_artist_list(artist)
    artist_matches = [
        match for match in matches
        if _description_matches_any_artist(match.get("description") or "", main_artists)
    ]
    fallback_matches = [
        match for match in matches
        if match not in artist_matches
        and _description_matches_fallback(match.get("description") or "", main_artists)
    ]
    return artist_matches + fallback_matches


@lru_cache(maxsize=1)
def _client() -> httpx.Client | None:
    return build_authenticated_client(API_URL, settings.wikidata_bot_username, settings.wikidata_bot_password)


def _delay_seconds() -> float:
    limit_per_minute = AUTHENTICATED_LIMIT_PER_MINUTE if _client() is not None else ANONYMOUS_LIMIT_PER_MINUTE
    return 60 / (limit_per_minute * RATE_LIMIT_TARGET_UTILIZATION)


_pacer = PersistedRequestPacer(STAGE_WIKIDATA)


def _get(params: dict) -> dict:
    _pacer.wait(_delay_seconds())
    request_started_at = time.monotonic()
    response = get_with_backoff(
        API_URL,
        params=params,
        headers={"User-Agent": METADATA_SOURCE_USER_AGENT},
        timeout=REQUEST_TIMEOUT_SECONDS,
        client=_client(),
    )
    _pacer.record_response_time(time.monotonic() - request_started_at)
    return response.json()


def search_entity(title: str, limit: int = DEFAULT_SEARCH_LIMIT) -> dict:
    """A combined "artist title" search returns nothing, wbsearchentities
    matches labels/aliases literally rather than doing free-text search;
    the artist is used only to disambiguate among these title-only results
    afterward, in pick_best_match, not sent to Wikidata itself."""
    return _get(
        {
            "action": "wbsearchentities",
            "search": title,
            "language": "en",
            "type": "item",
            "format": "json",
            "limit": limit,
        }
    )


def get_entity(entity_id: str) -> dict:
    return _get(
        {
            "action": "wbgetentities",
            "ids": entity_id,
            "props": "labels|claims",
            "languages": "en",
            "format": "json",
        }
    )


def get_sitelinks_count(entity_id: str) -> int | None:
    """Number of language-edition Wikipedia articles linked to this entity, the
    cold-start popularity proxy difficulty scoring falls back on before a song
    has real play history. A separate fetch from the labels and claims lookup
    above, since a sitelinks payload is large and only this count is ever used.
    Unknown when the fetch fails rather than zero, so a broken lookup never
    passes as an obscure song."""
    try:
        entity = _get({"action": "wbgetentities", "ids": entity_id, "props": "sitelinks", "format": "json"})["entities"][
            entity_id
        ]
    except PaceWaitTooLong:
        raise
    except Exception:
        return None
    sitelinks = entity.get("sitelinks")
    if not isinstance(sitelinks, dict):
        return None
    return len(sitelinks)


def pick_best_match(matches: list[dict], artist: str | list[str]) -> dict | None:
    """A title-only search can rank an unrelated homonym first. Prefers whichever
    match's description mentions any main artist.

    When nothing does, falls back only to a candidate whose description indicates
    a song or single and does not name another artist, returning None if even
    that comes up empty."""
    candidates = find_matching_candidates(matches, artist)
    return candidates[0] if candidates else None


def get_performer_ids(entity: dict) -> list[str]:
    claims = entity.get("claims", {})
    statements = claims.get(PERFORMER_PROPERTY, [])
    performer_ids = []
    for statement in statements:
        mainsnak = statement.get("mainsnak", {})
        if mainsnak.get("snaktype") == "value":
            datavalue = mainsnak.get("datavalue", {})
            value = datavalue.get("value")
            if isinstance(value, dict) and "id" in value:
                performer_ids.append(value["id"])
    return performer_ids


def get_performer_labels(performer_ids: list[str]) -> list[str]:
    if not performer_ids:
        return []
    response = _get(
        {
            "action": "wbgetentities",
            "ids": "|".join(performer_ids),
            "props": "labels",
            "languages": "en",
            "format": "json",
        }
    )
    entities = response.get("entities", {})
    labels = []
    for entity_id in performer_ids:
        entity = entities.get(entity_id, {})
        labels_dict = entity.get("labels", {})
        english_label = labels_dict.get("en", {}).get("value")
        if english_label:
            labels.append(english_label)
        elif labels_dict:
            first_entry = next(iter(labels_dict.values()), {})
            first_label = first_entry.get("value")
            if first_label:
                labels.append(first_label)
    return labels


def _performer_matches_main_artist(performer_labels: list[str], main_artists: list[str]) -> bool:
    for label in performer_labels:
        normalized_label = normalize_name(label)
        for artist in main_artists:
            normalized_artist = normalize_name(artist)
            if normalized_artist and (
                normalized_artist == normalized_label
                or normalized_artist in normalized_label
                or normalized_label in normalized_artist
            ):
                return True
    return False


def _confirm_performer(entity: dict, main_artists: list[str]) -> bool:
    performer_ids = get_performer_ids(entity)
    if not performer_ids:
        return True
    performer_labels = get_performer_labels(performer_ids)
    if not performer_labels:
        return False
    return _performer_matches_main_artist(performer_labels, main_artists)


def extract_publication_date(entity: dict) -> str | None:
    """A song entity can carry more than one P577 statement (the original
    release plus a later reissue/compilation date), and taking the first
    one listed isn't reliable, order isn't guaranteed to be earliest-first.
    Prefers a statement explicitly ranked "preferred" over "normal", then
    takes the earliest time value among whatever's left."""
    claims = entity.get("claims", {})
    statements = claims.get(PUBLICATION_DATE_PROPERTY)
    if not statements:
        return None

    preferred_statements = [statement for statement in statements if statement.get("rank") == "preferred"]
    candidate_statements = preferred_statements or statements
    publication_times = [
        statement["mainsnak"]["datavalue"]["value"]["time"]
        for statement in candidate_statements
        if statement.get("mainsnak", {}).get("snaktype") == "value"
    ]
    return min(publication_times) if publication_times else None


def get_part_of(entity: dict) -> str | None:
    """P361 ("part of") on a song entity usually points at its parent album.
    When present, this is more reliable than guessing the album's title and
    searching for it separately: no risk of a wrong guess, and no exposure
    to wbsearchentities' literal label matching for a second query."""
    claims = entity.get("claims", {})
    statements = claims.get(PART_OF_PROPERTY)
    if not statements:
        return None
    first_statement = statements[0]
    return first_statement["mainsnak"]["datavalue"]["value"]["id"]


def _resolve_candidate(entity_id: str, description: str | None, query_label: str) -> tuple[dict, dict]:
    entity = get_entity(entity_id)["entities"][entity_id]
    return {
        "query": query_label,
        "entity_id": entity_id,
        "description": description,
        "date": extract_publication_date(entity),
    }, entity


def search_or_raise(title: str, artist: str | list[str]) -> list[dict]:
    """Looks up the track, confirms the performer against the main artists when
    P175 is present, and looks up its parent album via P361. Returns every
    candidate found so downstream reconciliation sees the full picture. A failed
    request raises."""
    main_artists = to_artist_list(artist)
    matches = search_entity(title).get("search", [])
    candidate_matches = find_matching_candidates(matches, main_artists)
    if not candidate_matches:
        return []

    for match in candidate_matches[:MAX_WIKIDATA_CANDIDATE_ATTEMPTS]:
        track_candidate, track_entity = _resolve_candidate(match["id"], match.get("description"), "track")
        if not _confirm_performer(track_entity, main_artists):
            continue

        candidates = [track_candidate]
        album_entity_id = get_part_of(track_entity)
        if album_entity_id:
            album_candidate, _ = _resolve_candidate(album_entity_id, None, "album")
            candidates.append(album_candidate)

        return candidates

    return []


def search(title: str, artist: str | list[str]) -> list[dict]:
    """search_or_raise, reporting a failure and returning nothing instead of raising."""
    try:
        return search_or_raise(title, artist)
    except Exception as wikidata_error:
        artist_display = " & ".join(to_artist_list(artist))
        report_source_failure(SOURCE_NAME, wikidata_error, title, artist_display)
        return []
