import logging

import httpx

from app.metadata.sources.util import build_genius_search_url

logger = logging.getLogger(__name__)

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"


def search(title: str, artist: str) -> dict[str, str] | None:
    try:
        url = build_genius_search_url(artist, title)
        response = httpx.get(url, timeout=5.0, headers={"User-Agent": USER_AGENT})

        if response.status_code == 200:
            sections = response.json().get("response", {}).get("sections", [])

            for section in sections:
                hits = section.get("hits", [])
                if hits:
                    first_hit = hits[0].get("result", {})
                    return {
                        "title": first_hit.get("title", "unknown"),
                        "artist": first_hit.get("primary_artist", {}).get("name", "unknown"),
                        "release_date": first_hit.get("release_date_for_display", "unknown"),
                    }
    except Exception as e:
        logger.warning("Genius error: %s", e)

    return None
