import logging
import time

import httpx

from app.metadata.sources.util import build_musicbrainz_api_url, escape_lucene

logger = logging.getLogger(__name__)

USER_AGENT = "MusicMetadataApp/2.0 (example@example.com)"


def _build_queries(title: str, artist: str) -> list[str]:
    safe_title = escape_lucene(title)
    safe_artist = escape_lucene(artist)

    return [
        f'recording:"{safe_title}" AND artist:"{safe_artist}"',
        f'recording:"{safe_title}"',
        f'"{safe_title}" "{safe_artist}"',
        f"{safe_title} {safe_artist}",
    ]


def _parse_recording(rec: dict) -> dict[str, str]:
    artist_credit = rec.get("artist-credit") or []
    item = {
        "artist": artist_credit[0].get("name", "unknown") if artist_credit else "unknown",
        "title": rec.get("title", "unknown"),
        "release_date": rec.get("first-release-date", "unknown"),
        "score": str(rec.get("score", 0)),
    }

    tags = rec.get("tags") or []
    if tags:
        item["tags"] = ", ".join(tag.get("name", "") for tag in tags[:3]) + ", "

    return item


def search(title: str, artist: str) -> list[dict[str, str]]:
    results: list[dict[str, str]] = []

    try:
        for query in _build_queries(title, artist):
            api_url = build_musicbrainz_api_url(query)
            response = httpx.get(api_url, timeout=5.0, headers={"User-Agent": USER_AGENT})

            if response.status_code == 200:
                recordings = response.json().get("recordings", [])
                if recordings:
                    results.extend(_parse_recording(rec) for rec in recordings[:10])
                    if results:
                        break

            # MusicBrainz requires 1 request per second
            time.sleep(1)
    except Exception as e:
        logger.warning("MusicBrainz error: %s", e)

    return results
