"""Spike: batch-check every song in a YouTube playlist against MusicBrainz,
Discogs, and Wikidata (track-level only, album titles aren't known upfront
for an arbitrary playlist, unlike the hand-picked test matrix). Cross-checks
against each video's own "Released on:" description line where present,
auto-generated "Topic" channel uploads carry this from the label's own
metadata. See spikes/README.md.

Usage: python spikes/playlist_check.py <playlist_id>
"""

import re
import sys
from pathlib import Path

from dotenv import dotenv_values

import discogs_spike
import musicbrainz_spike
import wikidata_spike
import youtube_quota
from _shared import USER_AGENT, extract_year, get_with_backoff

_env = dotenv_values(Path(__file__).resolve().parent.parent / ".env")

RELEASED_ON_PATTERN = re.compile(r"Released on:\s*(?P<release_year>\d{4})-\d{2}-\d{2}")
TOPIC_SUFFIX_PATTERN = re.compile(r"\s*-\s*Topic$")

PLAYLIST_ITEMS_PAGE_SIZE = 50
PLAYLIST_ITEMS_LIST_COST_UNITS = 1


def fetch_playlist_items(playlist_id: str) -> list[dict]:
    items = []
    page_token = None
    while True:
        youtube_quota.charge(PLAYLIST_ITEMS_LIST_COST_UNITS, operation="playlistItems.list")
        params = {
            "part": "snippet",
            "maxResults": PLAYLIST_ITEMS_PAGE_SIZE,
            "playlistId": playlist_id,
            "key": _env["YOUTUBE_API_KEY"],
        }
        if page_token:
            params["pageToken"] = page_token
        response = get_with_backoff(
            "https://www.googleapis.com/youtube/v3/playlistItems", params=params, headers={"User-Agent": USER_AGENT}
        )
        data = response.json()
        items.extend(data.get("items", []))
        page_token = data.get("nextPageToken")
        if not page_token:
            break
    return items


def clean_artist(channel_title: str) -> str:
    return TOPIC_SUFFIX_PATTERN.sub("", channel_title or "").strip()


def extract_youtube_released_on(description: str | None) -> int | None:
    match = RELEASED_ON_PATTERN.search(description or "")
    if not match:
        return None
    return int(match.group("release_year"))


def check_musicbrainz(title: str, artist: str) -> int | None:
    data = musicbrainz_spike.search_release_group(title, artist)
    groups = data.get("release-groups", [])
    if not groups:
        return None
    best = musicbrainz_spike.select_best_release_group(groups)
    return extract_year(best.get("first-release-date"))


def check_discogs(title: str, artist: str) -> int | None:
    results = discogs_spike.search_release(title, artist)
    releases = results.get("results", [])
    if not releases:
        return None
    master_ids = discogs_spike.find_master_ids(releases)
    if not master_ids:
        return None

    master_years = []
    for master_id in master_ids:
        year = discogs_spike.master_year(discogs_spike.get_master(master_id))
        if year is not None:
            master_years.append(year)
    return min(master_years) if master_years else None


def check_wikidata(title: str, artist: str) -> int | None:
    matches = wikidata_spike.search_entity(title).get("search", [])
    if not matches:
        return None
    best = wikidata_spike.pick_best_match(matches, artist)
    if best is None:
        return None
    entity = wikidata_spike.get_entity(best["id"])["entities"][best["id"]]
    return extract_year(wikidata_spike.extract_publication_date(entity))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: playlist_check.py <playlist_id>")
        sys.exit(1)

    _script_path, playlist_id = sys.argv
    items = fetch_playlist_items(playlist_id)
    print(f"{len(items)} song(s) in playlist\n")

    mismatches = []
    for song_number, item in enumerate(items, start=1):
        snippet = item["snippet"]
        title = snippet["title"]
        artist = clean_artist(snippet.get("videoOwnerChannelTitle") or snippet.get("channelTitle"))
        youtube_year = extract_youtube_released_on(snippet.get("description"))

        print(f"--- {song_number}/{len(items)}: {title!r} by {artist!r} (YouTube desc: {youtube_year}) ---")

        musicbrainz_year = check_musicbrainz(title, artist)
        discogs_year = check_discogs(title, artist)
        wikidata_year = check_wikidata(title, artist)

        source_years = [
            year_value for year_value in (musicbrainz_year, discogs_year, wikidata_year) if year_value is not None
        ]
        agree = len(set(source_years)) <= 1 if source_years else False
        flag = "" if agree else "  <-- MISMATCH"
        print(
            f"    MusicBrainz={musicbrainz_year} Discogs={discogs_year} Wikidata={wikidata_year} "
            f"YouTube-desc={youtube_year}{flag}"
        )

        if not agree:
            mismatches.append((title, artist, musicbrainz_year, discogs_year, wikidata_year, youtube_year))

    print(f"\n{len(items)} checked, {len(mismatches)} source mismatch(es)")
    for title, artist, musicbrainz_year, discogs_year, wikidata_year, youtube_year in mismatches:
        print(
            f"  {title!r} by {artist!r}: MusicBrainz={musicbrainz_year} Discogs={discogs_year} "
            f"Wikidata={wikidata_year} YouTube={youtube_year}"
        )
