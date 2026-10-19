"""Runs Wikidata alone across all 49 songs (all_songs.py), scores against
ground truth, and caches each song's candidate entity data
(response_cache.py) for later reuse by the three-source combination test.
Its own branch, no LLM combination attached to it directly, meant to run
concurrently with run_full_musicbrainz.py and run_full_discogs.py. See
spikes/README.md.

Usage: python spikes/run_full_wikidata.py
"""

import time

import response_cache
import wikidata_spike
from _shared import RETRY_EVENT_COUNTS, extract_year, response_time_summary
from all_songs import ALL_SONGS, GROUND_TRUTH


def _lookup(title: str, artist: str, query_label: str) -> tuple[str | None, dict | None]:
    matches = wikidata_spike.search_entity(title).get("search", [])
    if not matches:
        return None, None

    best = wikidata_spike.pick_best_match(matches, artist)
    if best is None:
        return None, None

    entity_id = best["id"]
    entity = wikidata_spike.get_entity(entity_id)["entities"][entity_id]
    date = wikidata_spike.extract_publication_date(entity)
    candidate = {
        "query": query_label,
        "entity_id": entity_id,
        "description": best.get("description"),
        "date": date,
    }
    return date, candidate


def run_one(title: str, artist: str, album: str | None) -> int | None:
    track_date, track_candidate = _lookup(title, artist, "track")
    album_date = None
    album_candidate = None
    if album:
        album_date, album_candidate = _lookup(album, artist, "album")

    candidates = [candidate for candidate in (track_candidate, album_candidate) if candidate is not None]
    response_cache.save("wikidata", title, artist, candidates)

    years = [extract_year(date_value) for date_value in (track_date, album_date) if extract_year(date_value)]
    return min(years) if years else None


if __name__ == "__main__":
    run_started_at = time.perf_counter()
    correct = wrong = no_answer = 0

    for song_index, (title, artist, album, tier, note) in enumerate(ALL_SONGS, start=1):
        acceptable_years = GROUND_TRUTH[title]
        year = run_one(title, artist, album)
        if year is None:
            outcome = "no answer"
            no_answer += 1
        elif year in acceptable_years:
            outcome = "correct"
            correct += 1
        else:
            outcome = f"WRONG (said {year}, truth {acceptable_years})"
            wrong += 1
        print(f"[{song_index}/{len(ALL_SONGS)}] [{tier}] {title!r} by {artist!r}: {year} [{outcome}]")

    total = len(ALL_SONGS)
    total_seconds = time.perf_counter() - run_started_at
    print("\n=== Wikidata full-set results ===")
    print(f"{correct}/{total} correct ({correct / total:.0%}), {wrong} wrong, {no_answer} no answer")
    print(f"Total wall-clock time: {total_seconds:.1f}s ({total_seconds / total:.1f}s/song average)")
    if RETRY_EVENT_COUNTS:
        print(f"Retry/backoff events: {dict(RETRY_EVENT_COUNTS)}")
    for host, stats in response_time_summary().items():
        print(f"  {host}: {stats['count']} calls, avg {stats['average_seconds']:.2f}s")
