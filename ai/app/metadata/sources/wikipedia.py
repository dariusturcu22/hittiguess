import time
from functools import lru_cache

import httpx

from app.config import settings
from app.metadata.sources.http_retry import get_with_backoff
from app.metadata.sources.mediawiki_auth import build_authenticated_client
from app.metadata.sources.util import METADATA_SOURCE_USER_AGENT

API_URL = "https://en.wikipedia.org/w/api.php"
REQUEST_TIMEOUT_SECONDS = 10.0
SEARCH_RESULT_LIMIT = 5
EXTRACT_CHARACTER_LIMIT = 4000  # the lead section only, but some are long; caps prompt size later

# Same Wikimedia infrastructure as Wikidata: same rate-limit policy, same
# User-Agent policy, but a bot password is issued per wiki, Wikidata's
# doesn't authenticate here.
ANONYMOUS_LIMIT_PER_MINUTE = 10
AUTHENTICATED_LIMIT_PER_MINUTE = 200
RATE_LIMIT_TARGET_UTILIZATION = 0.67

_SONG_DISAMBIGUATOR_KEYWORDS = ("song", "single")
_ALBUM_DISAMBIGUATOR_KEYWORDS = ("album", "ep")


@lru_cache(maxsize=1)
def _client() -> httpx.Client | None:
    return build_authenticated_client(API_URL, settings.wikipedia_bot_username, settings.wikipedia_bot_password)


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


def search_page(title: str, artist: str) -> dict:
    """A real full-text query, not a literal label match the way Wikidata's
    wbsearchentities is, so the artist can be sent along with the title to
    help disambiguate at the search level instead of only after the fact."""
    return _get(
        {
            "action": "query",
            "list": "search",
            "srsearch": f"{title} {artist} song",
            "format": "json",
            "srlimit": SEARCH_RESULT_LIMIT,
        }
    )


def select_best_page(search_results: list[dict], title: str, artist: str, query_type: str = "track") -> dict | None:
    """Wikipedia's own naming convention already disambiguates a song from
    its own same-titled album (a song article's parenthetical says
    "song"/"single", an album's says "album"). That preference only matters
    among results that are actually about this title in the first place:
    filters to results whose own title contains the query title before
    applying any type preference, otherwise an unrelated same-artist song
    ranking in the top few results can get picked just for having "song" in
    its title.

    query_type flips which parenthetical is preferred: "track" (the
    default) prefers "song"/"single"; "album" prefers "album"/"ep" instead,
    and specifically avoids "song"/"single" results.

    When no result's title even contains the query title (some tracks have
    no dedicated article at all, only their parent album's, and some
    albums have no dedicated article either), the type preference isn't
    applied at all, that risks the exact same wrong-result failure the
    title-matching filter exists to prevent. Trusts Wikipedia's own
    relevance ranking (the top search result) instead, rather than
    guessing."""
    if not search_results:
        return None

    preferred_keywords = _SONG_DISAMBIGUATOR_KEYWORDS if query_type == "track" else _ALBUM_DISAMBIGUATOR_KEYWORDS
    avoided_keywords = _ALBUM_DISAMBIGUATOR_KEYWORDS if query_type == "track" else _SONG_DISAMBIGUATOR_KEYWORDS

    title_lower = title.lower()
    title_matching_results = [result for result in search_results if title_lower in result["title"].lower()]
    if not title_matching_results:
        return search_results[0]

    for result in title_matching_results:
        result_title_lower = result["title"].lower()
        if any(keyword in result_title_lower for keyword in preferred_keywords):
            return result

    for result in title_matching_results:
        result_title_lower = result["title"].lower()
        is_avoided_type = any(keyword in result_title_lower for keyword in avoided_keywords)
        is_bare_artist_page = result_title_lower == artist.lower()
        if not is_avoided_type and not is_bare_artist_page:
            return result

    return title_matching_results[0]


def get_lead_extract(page_title: str) -> str | None:
    """Plain-text lead section (the summary before the first heading), not
    the full article body: shorter, and a song/single article's release-
    year facts are typically stated there."""
    data = _get(
        {
            "action": "query",
            "prop": "extracts",
            "exintro": 1,
            "explaintext": 1,
            "titles": page_title,
            "format": "json",
        }
    )
    pages = data.get("query", {}).get("pages", {})
    for page in pages.values():
        extract = page.get("extract")
        if extract:
            return extract[:EXTRACT_CHARACTER_LIMIT]
    return None


def _find_and_extract(query_title: str, artist: str, query_label: str) -> dict | None:
    search_results = search_page(query_title, artist).get("query", {}).get("search", [])
    selected = select_best_page(search_results, query_title, artist, query_type=query_label)
    if selected is None:
        return None
    extract = get_lead_extract(selected["title"])
    if extract is None:
        return None
    return {"query": query_label, "page_title": selected["title"], "extract": extract}


def search(title: str, artist: str, album: str | None = None) -> list[dict]:
    """Looks up the track's own article and, when given, its parent
    album's, the same track-vs-album comparison MusicBrainz and Wikidata
    already do (see docs/DECISIONS.md's 2026-09 "Metadata pipeline
    call/reconcile shape" entry). Unlike those sources, Wikipedia has no
    structured release-year field: this returns each matched article's
    lead-section prose as-is, a dedicated LLM extraction pass (story 18)
    turns that into a year, not anything in this module."""
    try:
        entries = []

        track_entry = _find_and_extract(title, artist, "track")
        if track_entry:
            entries.append(track_entry)

        if album:
            album_entry = _find_and_extract(album, artist, "album")
            if album_entry:
                entries.append(album_entry)

        return entries
    except Exception:
        return []
