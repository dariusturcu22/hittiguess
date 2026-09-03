"""Spike: Wikidata entity search + P577 (publication date), see spikes/README.md.

Query on the title alone, a combined "artist title" search returns nothing,
wbsearchentities matches labels/aliases literally rather than doing free-text
search. The artist argument is used only to disambiguate among the title-only
results afterward, not sent to Wikidata itself.

Usage: python spikes/wikidata_spike.py "<title>" "<artist>"
"""

import sys

from _shared import USER_AGENT, get_with_backoff

API_URL = "https://www.wikidata.org/w/api.php"
DEFAULT_SEARCH_LIMIT = 20

PUBLICATION_DATE_PROPERTY = "P577"
PART_OF_PROPERTY = "P361"
COUNTRY_OF_ORIGIN_PROPERTY = "P495"
LANGUAGE_OF_WORK_PROPERTY = "P407"


def search_entity(title: str, limit: int = DEFAULT_SEARCH_LIMIT) -> dict:
    """A common title can bury the actual song many results past a narrow
    search window, a small limit risks never seeing the real entity at
    all."""
    response = get_with_backoff(
        API_URL,
        params={
            "action": "wbsearchentities",
            "search": title,
            "language": "en",
            "type": "item",
            "format": "json",
            "limit": limit,
        },
        headers={"User-Agent": USER_AGENT},
    )
    return response.json()


def get_entity(entity_id: str) -> dict:
    response = get_with_backoff(
        API_URL,
        params={
            "action": "wbgetentities",
            "ids": entity_id,
            "props": "labels|claims",
            "languages": "en",
            "format": "json",
        },
        headers={"User-Agent": USER_AGENT},
    )
    return response.json()


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
    release plus a later reissue/compilation date), and taking claims[0]
    isn't reliable, order isn't guaranteed to be earliest-first. Prefers a
    statement explicitly ranked "preferred" over "normal", then takes the
    earliest time value among whatever's left."""
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
    return statements[0]["mainsnak"]["datavalue"]["value"]["id"]


def get_sitelinks_count(entity_id: str) -> int:
    """Number of language-edition Wikipedia articles linked to this entity, a
    rough proxy for how internationally known something is: a song with
    dozens of language editions is plausibly more globally recognized than
    one with only its home-country language's article, or none."""
    response = get_with_backoff(
        API_URL,
        params={"action": "wbgetentities", "ids": entity_id, "props": "sitelinks", "format": "json"},
        headers={"User-Agent": USER_AGENT},
    )
    entity = response.json()["entities"][entity_id]
    return len(entity.get("sitelinks", {}))


def extract_entity_id_claim(entity: dict, property_id: str) -> str | None:
    """For claims whose value is itself a wikibase item (P495 country of origin,
    P407 language of work), returns the referenced item's Q-id, not its label,
    a second wbgetentities call would be needed to resolve the label."""
    claims = entity.get("claims", {})
    matching_claims = claims.get(property_id)
    if not matching_claims:
        return None
    return matching_claims[0]["mainsnak"]["datavalue"]["value"]["id"]


def resolve_labels(entity_ids: list[str]) -> dict[str, str]:
    if not entity_ids:
        return {}
    response = get_with_backoff(
        API_URL,
        params={
            "action": "wbgetentities",
            "ids": "|".join(entity_ids),
            "props": "labels",
            "languages": "en",
            "format": "json",
        },
        headers={"User-Agent": USER_AGENT},
    )
    entities = response.json()["entities"]
    return {
        entity_id: entities[entity_id]["labels"].get("en", {}).get("value", entity_id) for entity_id in entity_ids
    }


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: wikidata_spike.py <title> <artist>")
        sys.exit(1)

    title, artist = sys.argv[1], sys.argv[2]
    matches = search_entity(title).get("search", [])
    print(f"{len(matches)} entity match(es) for {title!r}")
    for match in matches:
        print(f"  {match['id']}: {match['label']} - {match.get('description')}")

    best_match = pick_best_match(matches, artist)
    if best_match:
        top_id = best_match["id"]
        if top_id != matches[0]["id"]:
            print(f"(disambiguated to {top_id} over top-ranked {matches[0]['id']})")
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
