import time
from functools import lru_cache

import httpx

from app.config import settings
from app.metadata.sources.http_retry import get_with_backoff
from app.metadata.sources.mediawiki_auth import build_authenticated_client
from app.metadata.sources.util import METADATA_SOURCE_USER_AGENT
from app.observability.error_reporting import report_source_failure

SOURCE_NAME = "wikidata"
API_URL = "https://www.wikidata.org/w/api.php"
REQUEST_TIMEOUT_SECONDS = 10.0
DEFAULT_SEARCH_LIMIT = 20  # a common title can bury the real song many results down a narrow window

PUBLICATION_DATE_PROPERTY = "P577"
PART_OF_PROPERTY = "P361"

# Confirmed against mediawiki.org/wiki/Wikimedia_APIs/Rate_limits: 10/min applies to
# requests with no identifying characteristics beyond IP; any logged-in account, no
# approval needed, gets 200/min.
ANONYMOUS_LIMIT_PER_MINUTE = 10
AUTHENTICATED_LIMIT_PER_MINUTE = 200
RATE_LIMIT_TARGET_UTILIZATION = 0.67

_MUSIC_DESCRIPTION_KEYWORDS = ("single", "song", "album", "track", " ep", "recording", "record")


@lru_cache(maxsize=1)
def _client() -> httpx.Client | None:
    return build_authenticated_client(API_URL, settings.wikidata_bot_username, settings.wikidata_bot_password)


def _delay_seconds() -> float:
    limit_per_minute = AUTHENTICATED_LIMIT_PER_MINUTE if _client() is not None else ANONYMOUS_LIMIT_PER_MINUTE
    return 60 / (limit_per_minute * RATE_LIMIT_TARGET_UTILIZATION)


def _get(params: dict) -> dict:
    time.sleep(_delay_seconds())
    response = get_with_backoff(
        API_URL,
        params=params,
        headers={"User-Agent": METADATA_SOURCE_USER_AGENT},
        timeout=REQUEST_TIMEOUT_SECONDS,
        client=_client(),
    )
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


def pick_best_match(matches: list[dict], artist: str) -> dict | None:
    """A title-only search (the only kind that reliably returns results, see
    search_entity) can rank an unrelated homonym first. Prefers whichever
    match's description mentions the artist name.

    When nothing does, blindly falling back to the top-ranked match is
    actively harmful, not neutral: it can confidently return a wrong entity
    with the same presentation as a right one. Falls back only to a
    candidate whose own description at least sounds like a music release,
    and returns None (better than a wrong entity) if even that comes up
    empty."""
    if not matches:
        return None

    artist_lower = artist.lower()
    for match in matches:
        description = (match.get("description") or "").lower()
        if artist_lower in description:
            return match

    for match in matches:
        description = (match.get("description") or "").lower()
        if any(keyword in description for keyword in _MUSIC_DESCRIPTION_KEYWORDS):
            return match

    return None


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


def search(title: str, artist: str) -> list[dict]:
    """Looks up the track, then its parent album via the resolved entity's
    own P361 claim when present, so both a track-level and an album-level
    release date can be compared without guessing the album's title (see
    docs/DECISIONS.md's 2026-09 "Metadata pipeline call/reconcile shape"
    entry). Returns every candidate found, not just the earliest, so
    downstream reconciliation can see the full picture a single pick would
    hide."""
    try:
        matches = search_entity(title).get("search", [])
        best_match = pick_best_match(matches, artist)
        if best_match is None:
            return []

        track_candidate, track_entity = _resolve_candidate(best_match["id"], best_match.get("description"), "track")
        candidates = [track_candidate]

        album_entity_id = get_part_of(track_entity)
        if album_entity_id:
            album_candidate, _ = _resolve_candidate(album_entity_id, None, "album")
            candidates.append(album_candidate)

        return candidates
    except Exception as wikidata_error:
        report_source_failure(SOURCE_NAME, wikidata_error, title, artist)
        return []
