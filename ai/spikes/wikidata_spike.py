"""Spike: Wikidata entity search + P577 (publication date), see spikes/README.md.

Query on the title alone, a combined "artist title" search returns nothing,
wbsearchentities matches labels/aliases literally rather than doing free-text
search. The artist argument is used only to disambiguate among the title-only
results afterward, not sent to Wikidata itself.

Usage: python spikes/wikidata_spike.py "<title>" "<artist>"
"""

import sys
import time
from pathlib import Path

import httpx
from dotenv import dotenv_values

from _shared import RATE_LIMIT_TARGET_UTILIZATION, USER_AGENT, get_with_backoff

_env = dotenv_values(Path(__file__).resolve().parent.parent / ".env")

API_URL = "https://www.wikidata.org/w/api.php"
DEFAULT_SEARCH_LIMIT = 20

PUBLICATION_DATE_PROPERTY = "P577"
PART_OF_PROPERTY = "P361"
COUNTRY_OF_ORIGIN_PROPERTY = "P495"
LANGUAGE_OF_WORK_PROPERTY = "P407"

# Confirmed against mediawiki.org/wiki/Wikimedia_APIs/Rate_limits: 10/min applies to
# requests with no identifying characteristics beyond IP; any logged-in account, no
# approval needed, gets 200/min. See spikes/README.md for how to get a bot password.
ANONYMOUS_LIMIT_PER_MINUTE = 10
AUTHENTICATED_LIMIT_PER_MINUTE = 200


def _build_authenticated_client() -> httpx.Client | None:
    """A Special:BotPasswords login (WIKIDATA_BOT_USERNAME/PASSWORD in
    ai/.env) unlocks the 200/minute authenticated tier. Falls back to
    anonymous access, and the slower 10/minute tier, if unset. MediaWiki's
    login flow is a two-step token-then-login exchange, not HTTP Basic
    Auth, and requires persisting the session cookie across every
    subsequent call, hence a real httpx.Client rather than one-off
    requests."""
    bot_username = _env.get("WIKIDATA_BOT_USERNAME")
    bot_password = _env.get("WIKIDATA_BOT_PASSWORD")
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
        raise RuntimeError(f"Wikidata bot-password login failed: {login_response.json()}")
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


def search_entity(title: str, limit: int = DEFAULT_SEARCH_LIMIT) -> dict:
    """A common title can bury the actual song many results past a narrow
    search window, a small limit risks never seeing the real entity at
    all."""
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


_MUSIC_DESCRIPTION_KEYWORDS = ("single", "song", "album", "track", " ep", "recording", "record")


def pick_best_match(matches: list[dict], artist: str) -> dict | None:
    """A title-only search (the only kind that reliably returns results, see
    module docstring) can rank an unrelated homonym first. Prefers whichever
    match's description mentions the artist name.

    When nothing does, blindly falling back to the top-ranked match is
    actively harmful, not neutral, it can confidently return a wrong entity
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
    """P361 ("part of") on a song entity usually points at its parent album,
    when present this is more reliable than guessing the album's title and
    searching for it separately: no risk of a wrong guess, and no exposure
    to wbsearchentities' literal label matching for a second query."""
    claims = entity.get("claims", {})
    statements = claims.get(PART_OF_PROPERTY)
    if not statements:
        return None
    first_statement = statements[0]
    return first_statement["mainsnak"]["datavalue"]["value"]["id"]


def get_sitelinks_count(entity_id: str) -> int:
    """Number of language-edition Wikipedia articles linked to this entity, a
    rough proxy for how internationally known something is: a song with
    dozens of language editions is plausibly more globally recognized than
    one with only its home-country language's article, or none."""
    entity = _get({"action": "wbgetentities", "ids": entity_id, "props": "sitelinks", "format": "json"})["entities"][
        entity_id
    ]
    return len(entity.get("sitelinks", {}))


def extract_entity_id_claim(entity: dict, property_id: str) -> str | None:
    """For claims whose value is itself a wikibase item (P495 country of origin,
    P407 language of work), returns the referenced item's Q-id, not its label,
    a second wbgetentities call would be needed to resolve the label."""
    claims = entity.get("claims", {})
    matching_claims = claims.get(property_id)
    if not matching_claims:
        return None
    first_matching_claim = matching_claims[0]
    return first_matching_claim["mainsnak"]["datavalue"]["value"]["id"]


def resolve_labels(entity_ids: list[str]) -> dict[str, str]:
    if not entity_ids:
        return {}
    entities = _get(
        {
            "action": "wbgetentities",
            "ids": "|".join(entity_ids),
            "props": "labels",
            "languages": "en",
            "format": "json",
        }
    )["entities"]
    return {
        entity_id: entities[entity_id]["labels"].get("en", {}).get("value", entity_id) for entity_id in entity_ids
    }


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: wikidata_spike.py <title> <artist>")
        sys.exit(1)

    _script_path, title, artist = sys.argv
    matches = search_entity(title).get("search", [])
    print(f"{len(matches)} entity match(es) for {title!r}")
    for match in matches:
        print(f"  {match['id']}: {match['label']} - {match.get('description')}")

    best_match = pick_best_match(matches, artist)
    if best_match:
        top_id = best_match["id"]
        top_ranked_match_id = matches[0]["id"]
        if top_id != top_ranked_match_id:
            print(f"(disambiguated to {top_id} over top-ranked {top_ranked_match_id})")
        entity = get_entity(top_id)["entities"][top_id]
        date = extract_publication_date(entity)
        country_id = extract_entity_id_claim(entity, COUNTRY_OF_ORIGIN_PROPERTY)
        language_id = extract_entity_id_claim(entity, LANGUAGE_OF_WORK_PROPERTY)
        labels = resolve_labels([entity_id for entity_id in (country_id, language_id) if entity_id])
        sitelinks_count = get_sitelinks_count(top_id)
        print(f"\nTop match {top_id} P577 (publication date): {date}")
        print(f"Top match {top_id} P495 (country of origin): {labels.get(country_id, country_id)}")
        print(f"Top match {top_id} P407 (language of work): {labels.get(language_id, language_id)}")
        print(f"Top match {top_id} sitelinks (Wikipedia language editions): {sitelinks_count}")
