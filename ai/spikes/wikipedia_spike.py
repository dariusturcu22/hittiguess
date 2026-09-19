"""Spike: English Wikipedia full-text search + lead-section extract, see
spikes/README.md.

Unlike Wikidata's wbsearchentities (literal label matching, title-only),
Wikipedia's list=search is real full-text search and handles a combined
"title artist song" query directly, so the artist is sent as part of the
query itself, not just used to disambiguate afterward.

Wikipedia has no structured release-year field the way Wikidata's P577
claim is: this returns the lead section as plain prose, an LLM extraction
pass is what turns that into a year (see combo_prompts.py), not anything
in this module.

Same Wikimedia infrastructure as Wikidata (confirmed: same rate-limit
policy, same User-Agent policy, mediawiki.org/wiki/Wikimedia_APIs/Rate_limits
states limits apply "across all sites and platforms"), but a bot password
is issued per-wiki, the existing Wikidata one won't authenticate here;
falls back to the same 10/minute anonymous tier if
WIKIPEDIA_BOT_USERNAME/PASSWORD aren't set in ai/.env.

Usage: python spikes/wikipedia_spike.py "<title>" "<artist>"
"""

import sys
import time
from pathlib import Path

import httpx
from dotenv import dotenv_values

from _shared import RATE_LIMIT_TARGET_UTILIZATION, USER_AGENT, get_with_backoff

_env = dotenv_values(Path(__file__).resolve().parent.parent / ".env")

API_URL = "https://en.wikipedia.org/w/api.php"
SEARCH_RESULT_LIMIT = 5
EXTRACT_CHARACTER_LIMIT = 4000  # the lead section only, but some are long; caps prompt size later

ANONYMOUS_LIMIT_PER_MINUTE = 10
AUTHENTICATED_LIMIT_PER_MINUTE = 200


def _build_authenticated_client() -> httpx.Client | None:
    bot_username = _env.get("WIKIPEDIA_BOT_USERNAME")
    bot_password = _env.get("WIKIPEDIA_BOT_PASSWORD")
    if not bot_username or not bot_password:
        return None

    client = httpx.Client(headers={"User-Agent": USER_AGENT})
    token_response = client.get(
        API_URL, params={"action": "query", "meta": "tokens", "type": "login", "format": "json"}
    )
    login_token = token_response.json()["query"]["tokens"]["logintoken"]
    login_response = client.post(
        API_URL,
        data={
            "action": "login",
            "lgname": bot_username,
            "lgpassword": bot_password,
            "lgtoken": login_token,
            "format": "json",
        },
    )
    login_result = login_response.json().get("login", {}).get("result")
    if login_result != "Success":
        raise RuntimeError(f"Wikipedia bot-password login failed: {login_response.json()}")
    return client


_authenticated_client = _build_authenticated_client()
DELAY_SECONDS = 60 / (
    (AUTHENTICATED_LIMIT_PER_MINUTE if _authenticated_client else ANONYMOUS_LIMIT_PER_MINUTE)
    * RATE_LIMIT_TARGET_UTILIZATION
)


def _get(params: dict) -> dict:
    time.sleep(DELAY_SECONDS)
    response = get_with_backoff(
        API_URL, params=params, headers={"User-Agent": USER_AGENT}, client=_authenticated_client
    )
    return response.json()


def search_page(title: str, artist: str) -> dict:
    """A real full-text query, not a literal label match, so the artist
    can be sent along with the title to help disambiguate at the search
    level instead of only after the fact."""
    return _get(
        {
            "action": "query",
            "list": "search",
            "srsearch": f"{title} {artist} song",
            "format": "json",
            "srlimit": SEARCH_RESULT_LIMIT,
        }
    )


_SONG_DISAMBIGUATOR_KEYWORDS = ("song", "single")
_ALBUM_DISAMBIGUATOR_KEYWORDS = ("album", "ep")


def select_best_page(
    search_results: list[dict], title: str, artist: str, query_type: str = "track"
) -> dict | None:
    """Wikipedia's own naming convention already disambiguates a song from
    its own same-titled album (a song article's parenthetical says
    "song"/"single", an album's says "album"), unlike Wikidata or
    MusicBrainz, which both needed real disambiguation logic against
    ambiguous or absent type metadata. That preference only matters among
    results that are actually about this title in the first place:
    filters to results whose own title contains the query title before
    applying any type preference, otherwise an unrelated same-artist song
    ranking in the top few results can get picked just for having "song"
    in its title.

    query_type flips which parenthetical is preferred: "track" (the
    default) prefers "song"/"single"; "album" prefers "album"/"ep"
    instead, and specifically avoids "song"/"single" results. Album
    lookups need the opposite preference from track lookups, an
    unrelated same-titled work in a different medium can otherwise be
    picked over the actual album.

    When no result's title even contains the query title (some tracks
    have no dedicated article at all, only their parent album's, and
    some albums have no dedicated article either), the type preference
    isn't applied at all, that risks the exact same wrong-result failure
    the title-matching filter exists to prevent. Trusts Wikipedia's own
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


def find_and_extract(title: str, artist: str, query_type: str = "track") -> tuple[str, str] | tuple[None, None]:
    """Combined search + select + fetch for one title (a song's own title,
    or its parent album's, query_type="album" for the latter, so
    select_best_page prefers the right kind of result), the shared step
    behind checking both a track and its album the same way MusicBrainz,
    Discogs, and Wikidata all already do, a comparison Wikipedia was
    missing until this existed. Returns (page_title, lead_extract), or
    (None, None) if nothing usable was found."""
    search_results = search_page(title, artist).get("query", {}).get("search", [])
    selected = select_best_page(search_results, title, artist, query_type)
    if selected is None:
        return None, None
    extract = get_lead_extract(selected["title"])
    if extract is None:
        return None, None
    return selected["title"], extract


def get_lead_extract(page_title: str) -> str | None:
    """Plain-text lead section (the summary before the first heading),
    not the full article body: shorter, and release-year facts are
    typically stated there for a song/single article, per the "Hot"
    (Inna) case that prompted this spike."""
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


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: wikipedia_spike.py <title> <artist>")
        sys.exit(1)

    _script_path, title, artist = sys.argv
    search_results = search_page(title, artist).get("query", {}).get("search", [])
    print(f"{len(search_results)} search result(s) for {title!r} by {artist!r}:")
    for result in search_results:
        print(f"  {result['title']!r} (size={result.get('size')}, wordcount={result.get('wordcount')})")

    if not search_results:
        sys.exit(0)

    selected = select_best_page(search_results, title, artist)
    disambiguated = selected is not search_results[0]
    print(f"\nSelected: {selected['title']!r}{' [disambiguated away from top rank]' if disambiguated else ''}")
    extract = get_lead_extract(selected["title"])
    print(f"\n=== Lead extract for {selected['title']!r} ===")
    print(extract)
