"""Spike: Wikidata entity search + P577 (publication date), see spikes/README.md.

Query on the title alone, a combined "artist title" search returns nothing,
wbsearchentities matches labels/aliases literally rather than doing free-text
search.

Usage: python spikes/wikidata_spike.py "<title>"
"""

import sys

from _shared import USER_AGENT, get_with_backoff

API_URL = "https://www.wikidata.org/w/api.php"


def search_entity(title: str) -> dict:
    response = get_with_backoff(
        API_URL,
        params={
            "action": "wbsearchentities",
            "search": title,
            "language": "en",
            "type": "item",
            "format": "json",
            "limit": 5,
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


def extract_publication_date(entity: dict) -> str | None:
    claims = entity.get("claims", {})
    publication_date_claims = claims.get("P577")
    if not publication_date_claims:
        return None
    return publication_date_claims[0]["mainsnak"]["datavalue"]["value"]["time"]


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
    return {eid: entities[eid]["labels"].get("en", {}).get("value", eid) for eid in entity_ids}


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: wikidata_spike.py <title>")
        sys.exit(1)

    title = sys.argv[1]
    matches = search_entity(title).get("search", [])
    print(f"{len(matches)} entity match(es) for {title!r}")
    for match in matches:
        print(f"  {match['id']}: {match['label']} - {match.get('description')}")

    if matches:
        top_id = matches[0]["id"]
        entity = get_entity(top_id)["entities"][top_id]
        date = extract_publication_date(entity)
        country_id = extract_entity_id_claim(entity, "P495")
        language_id = extract_entity_id_claim(entity, "P407")
        labels = resolve_labels([i for i in (country_id, language_id) if i])
        print(f"\nTop match {top_id} P577 (publication date): {date}")
        print(f"Top match {top_id} P495 (country of origin): {labels.get(country_id, country_id)}")
        print(f"Top match {top_id} P407 (language of work): {labels.get(language_id, language_id)}")
