"""Fetches and caches Wikipedia's lead-section extract for all 49 songs
(all_songs.py), no LLM extraction or scoring here, that's a separate step
once the cache is populated. Looks up both the song's own article and, if
given, its parent album's, the same track-vs-album comparison
MusicBrainz, Discogs, and Wikidata already do, Wikipedia was the one
source missing it until now. Prints which page got selected for each
lookup and flags every case where the search's top-ranked result wasn't
the one picked, so the disambiguation logic (select_best_page) can be
eyeballed against the real 49-song set rather than trusted blindly. See
spikes/README.md.

Usage: python spikes/run_full_wikipedia.py
"""

import time

import response_cache
import wikipedia_spike
from _shared import RETRY_EVENT_COUNTS, response_time_summary
from all_songs import ALL_SONGS


def _lookup(query_title: str, artist: str, query_label: str) -> dict | None:
    search_results = wikipedia_spike.search_page(query_title, artist).get("query", {}).get("search", [])
    selected = wikipedia_spike.select_best_page(search_results, query_title, artist, query_type=query_label)
    if selected is None:
        print(f"    ({query_label}) no search match")
        return None

    disambiguated = selected is not search_results[0]
    extract = wikipedia_spike.get_lead_extract(selected["title"])
    if extract is None:
        print(f"    ({query_label}) selected {selected['title']!r} but no usable extract")
        return None

    flag = " [disambiguated]" if disambiguated else ""
    print(f"    ({query_label}) selected {selected['title']!r}{flag}")
    return {"query": query_label, "page_title": selected["title"], "extract": extract, "disambiguated": disambiguated}


if __name__ == "__main__":
    run_started_at = time.perf_counter()
    disambiguated_count = 0
    track_no_match_count = 0
    songs_with_no_data_at_all = 0

    for song_index, (title, artist, album, tier, note) in enumerate(ALL_SONGS, start=1):
        print(f"[{song_index}/{len(ALL_SONGS)}] [{tier}] {title!r} by {artist!r}")

        track_entry = _lookup(title, artist, "track")
        if track_entry is None:
            track_no_match_count += 1
        elif track_entry["disambiguated"]:
            disambiguated_count += 1

        album_entry = _lookup(album, artist, "album") if album else None
        if album_entry and album_entry["disambiguated"]:
            disambiguated_count += 1

        entries = [entry for entry in (track_entry, album_entry) if entry is not None]
        if not entries:
            songs_with_no_data_at_all += 1
        response_cache.save("wikipedia", title, artist, entries)

    total = len(ALL_SONGS)
    total_seconds = time.perf_counter() - run_started_at
    print("\n=== Wikipedia full-set fetch results ===")
    print(f"{total - track_no_match_count} of {total} had a track-level match, {track_no_match_count} had none")
    print(f"{songs_with_no_data_at_all} of {total} had no usable data at all (neither track nor album)")
    print(f"{disambiguated_count} lookups (track or album) needed disambiguation away from the top-ranked result")
    print(f"Total wall-clock time: {total_seconds:.1f}s ({total_seconds / total:.1f}s/song average)")
    if RETRY_EVENT_COUNTS:
        print(f"Retry/backoff events: {dict(RETRY_EVENT_COUNTS)}")
    for host, stats in response_time_summary().items():
        print(f"  {host}: {stats['count']} calls, avg {stats['average_seconds']:.2f}s")
