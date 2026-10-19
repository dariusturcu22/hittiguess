"""Fetches and caches all four sources for just the 21 new songs in
mainstream_test_songs.py, reusing each source's existing per-song lookup
logic. The other 49 songs already have cached data from earlier runs,
re-fetching them here would waste real API budget for no new information.
See spikes/README.md.

Usage: python spikes/run_new_batch_only.py
"""

import time

import discogs_spike
import musicbrainz_spike
import response_cache
import wikidata_spike
import wikipedia_spike
from _shared import RETRY_EVENT_COUNTS, extract_year
from mainstream_test_songs import GROUND_TRUTH, MAINSTREAM_TEST_SONGS
from run_full_discogs import run_one as discogs_run_one
from run_full_musicbrainz import run_one as musicbrainz_run_one
from run_full_wikidata import run_one as wikidata_run_one
from run_full_wikipedia import _lookup as wikipedia_lookup

if __name__ == "__main__":
    run_started_at = time.perf_counter()
    musicbrainz_correct = discogs_correct = wikidata_correct = 0

    for song_index, (title, artist, album, tier, note) in enumerate(MAINSTREAM_TEST_SONGS, start=1):
        acceptable_years = GROUND_TRUTH[title]
        print(f"[{song_index}/{len(MAINSTREAM_TEST_SONGS)}] [{tier}] {title!r} by {artist!r}")

        musicbrainz_year = musicbrainz_run_one(title, artist, album)
        musicbrainz_outcome = (
            "correct" if musicbrainz_year in acceptable_years else f"WRONG/no-answer ({musicbrainz_year})"
        )
        if musicbrainz_year in acceptable_years:
            musicbrainz_correct += 1
        print(f"    MusicBrainz: {musicbrainz_year} [{musicbrainz_outcome}]")

        discogs_year = discogs_run_one(title, artist, album)
        discogs_outcome = "correct" if discogs_year in acceptable_years else f"WRONG/no-answer ({discogs_year})"
        if discogs_year in acceptable_years:
            discogs_correct += 1
        print(f"    Discogs: {discogs_year} [{discogs_outcome}]")

        wikidata_year = wikidata_run_one(title, artist, album)
        wikidata_outcome = "correct" if wikidata_year in acceptable_years else f"WRONG/no-answer ({wikidata_year})"
        if wikidata_year in acceptable_years:
            wikidata_correct += 1
        print(f"    Wikidata: {wikidata_year} [{wikidata_outcome}]")

        track_entry = wikipedia_lookup(title, artist, "track")
        album_entry = wikipedia_lookup(album, artist, "album") if album else None
        wikipedia_entries = [entry for entry in (track_entry, album_entry) if entry is not None]
        response_cache.save("wikipedia", title, artist, wikipedia_entries)
        print(f"    Wikipedia: cached {len(wikipedia_entries)} extract(s)")

    total = len(MAINSTREAM_TEST_SONGS)
    total_seconds = time.perf_counter() - run_started_at
    print("\n=== New-batch fetch results (structured sources only, Wikipedia needs extraction separately) ===")
    print(f"MusicBrainz: {musicbrainz_correct}/{total} correct")
    print(f"Discogs: {discogs_correct}/{total} correct")
    print(f"Wikidata: {wikidata_correct}/{total} correct")
    print(f"Total wall-clock time: {total_seconds:.1f}s")
    if RETRY_EVENT_COUNTS:
        print(f"Retry/backoff events: {dict(RETRY_EVENT_COUNTS)}")
