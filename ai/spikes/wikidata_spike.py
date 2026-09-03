"""Spike: Wikidata entity search + P577 (publication date), see spikes/README.md.

Query on the title alone, a combined "artist title" search returns nothing,
wbsearchentities matches labels/aliases literally rather than doing free-text
search.

Usage: python spikes/wikidata_spike.py "<title>"
"""

import sys

import httpx
from _shared import USER_AGENT

API_URL = "https://www.wikidata.org/w/api.php"


def search_entity(title: str) -> dict:
    response = httpx.get(
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
        timeout=10.0,
    )
    response.raise_for_status()
    return response.json()


def get_entity(entity_id: str) -> dict:
    response = httpx.get(
        API_URL,
        params={
            "action": "wbgetentities",
            "ids": entity_id,
            "props": "labels|claims",
            "languages": "en",
            "format": "json",
        },
        headers={"User-Agent": USER_AGENT},
        timeout=10.0,
    )
    response.raise_for_status()
    return response.json()


def extract_publication_date(entity: dict) -> str | None:
    claims = entity.get("claims", {})
    publication_date_claims = claims.get("P577")
    if not publication_date_claims:
        return None
    return publication_date_claims[0]["mainsnak"]["datavalue"]["value"]["time"]


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
        print(f"\nTop match {top_id} P577 (publication date): {date}")
