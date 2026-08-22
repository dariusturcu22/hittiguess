import logging
import time

import httpx

from app.metadata.sources.util import (
    build_wikipedia_page_url,
    build_wikipedia_search_url,
    extract_release_date_from_text,
)

logger = logging.getLogger(__name__)

USER_AGENT = "MusicMetadataApp/2.0"


def _fetch_page_content(page_title: str) -> dict[str, str] | None:
    try:
        content_url = build_wikipedia_page_url(page_title)
        response = httpx.get(content_url, timeout=5.0, headers={"User-Agent": USER_AGENT})
        pages = response.json().get("query", {}).get("pages", {})

        if pages:
            page = next(iter(pages.values()))
            extract = page.get("extract", "")
            release_info = extract_release_date_from_text(extract)

            if release_info != "No release date found":
                return {"title": page_title, "release_info": release_info}
    except Exception as e:
        logger.warning("Wikipedia page fetch error: %s", e)

    return None


def search(title: str, artist: str) -> dict[str, str] | None:
    try:
        search_terms = [f"{title} {artist} song", f"{title} (song)", title]

        for search_term in search_terms:
            url = build_wikipedia_search_url(search_term)
            response = httpx.get(url, timeout=5.0, headers={"User-Agent": USER_AGENT})

            if response.status_code == 200:
                search_results = response.json().get("query", {}).get("search", [])

                if search_results:
                    page_title = search_results[0].get("title", "")
                    result = _fetch_page_content(page_title)
                    if result is not None:
                        return result

            time.sleep(0.5)
    except Exception as e:
        logger.warning("Wikipedia error: %s", e)

    return None
