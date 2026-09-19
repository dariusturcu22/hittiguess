"""Runs Discogs alone across all 49 songs (all_songs.py), scores against
ground truth, and caches each song's candidate masters (response_cache.py)
for later reuse by the LLM+Discogs-only combination test. Independent of
MusicBrainz and Wikidata, meant to run concurrently with
run_full_musicbrainz.py and run_full_wikidata.py, not after either. See
spikes/README.md.

Usage: python spikes/run_full_discogs.py
"""

import time

import discogs_spike
import response_cache
from _shared import RETRY_EVENT_COUNTS, response_time_summary
from all_songs import ALL_SONGS, GROUND_TRUTH


def _lookup(title: str, artist: str, query_label: str) -> tuple[int | None, list[dict]]:
    results = discogs_spike.search_release(title, artist)
    releases = results.get("results", [])
    if not releases:
        return None, []

    master_ids = discogs_spike.find_master_ids(releases)
    candidates = []
    years = []
    for master_id in master_ids:
        master = discogs_spike.get_master(master_id)
        year = discogs_spike.master_year(master)
        candidates.append(
            {
                "query": query_label,
                "title": master.get("title"),
                "master_id": master_id,
                "year": year,
                "format": master.get("styles"),
            }
        )
        if year is not None:
            years.append(year)

    for masterless in discogs_spike.masterless_release_years(releases):
        candidates.append(
            {
                "query": query_label,
                "title": masterless["title"],
                "master_id": None,
                "year": masterless["year"],
                "format": None,
            }
        )
        years.append(masterless["year"])

    return (min(years) if years else None), candidates


def run_one(title: str, artist: str, album: str | None) -> int | None:
    track_year, track_candidates = _lookup(title, artist, "track")
    album_year = None
    album_candidates: list[dict] = []
    if album:
        album_year, album_candidates = _lookup(album, artist, "album")

    response_cache.save("discogs", title, artist, track_candidates + album_candidates)

    years = [year_value for year_value in (track_year, album_year) if year_value is not None]
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
    print("\n=== Discogs full-set results ===")
    print(f"{correct}/{total} correct ({correct / total:.0%}), {wrong} wrong, {no_answer} no answer")
    print(f"Total wall-clock time: {total_seconds:.1f}s ({total_seconds / total:.1f}s/song average)")
    if RETRY_EVENT_COUNTS:
        print(f"Retry/backoff events: {dict(RETRY_EVENT_COUNTS)}")
    for host, stats in response_time_summary().items():
        print(f"  {host}: {stats['count']} calls, avg {stats['average_seconds']:.2f}s")
