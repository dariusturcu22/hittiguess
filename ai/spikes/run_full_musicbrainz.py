"""Runs MusicBrainz alone across all 49 songs (all_songs.py), scores
against ground truth, and caches each song's candidate release-groups
(response_cache.py) for later reuse by the LLM+MusicBrainz-only
combination test. Independent of Discogs and Wikidata, meant to run
concurrently with run_full_discogs.py and run_full_wikidata.py, not after
either. See spikes/README.md.

Usage: python spikes/run_full_musicbrainz.py
"""

import time

import musicbrainz_spike
import response_cache
from _shared import RETRY_EVENT_COUNTS, extract_year, response_time_summary
from all_songs import ALL_SONGS, GROUND_TRUTH

CANDIDATES_TO_KEEP_PER_QUERY = 5


def _candidates_from_groups(groups: list[dict], query_label: str) -> list[dict]:
    candidates = []
    for group in groups[:CANDIDATES_TO_KEEP_PER_QUERY]:
        artist_credit = ", ".join(credit["name"] for credit in group.get("artist-credit", []))
        candidates.append(
            {
                "query": query_label,
                "title": group.get("title"),
                "artist": artist_credit,
                "date": group.get("first-release-date"),
                "type": group.get("primary-type"),
                "score": group.get("score"),
            }
        )
    return candidates


def run_one(title: str, artist: str, album: str | None) -> int | None:
    candidates: list[dict] = []

    track_data = musicbrainz_spike.search_release_group(title, artist)
    track_groups = track_data.get("release-groups", [])
    candidates += _candidates_from_groups(track_groups, "track")
    track_best = musicbrainz_spike.select_best_release_group(track_groups) if track_groups else None
    track_date = track_best.get("first-release-date") if track_best else None

    album_date = None
    if album:
        album_data = musicbrainz_spike.search_release_group(album, artist)
        album_groups = album_data.get("release-groups", [])
        candidates += _candidates_from_groups(album_groups, "album")
        album_best = (
            musicbrainz_spike.select_best_release_group(album_groups, prefer_type="Album") if album_groups else None
        )
        album_date = album_best.get("first-release-date") if album_best else None

    response_cache.save("musicbrainz", title, artist, candidates)

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
    print("\n=== MusicBrainz full-set results ===")
    print(f"{correct}/{total} correct ({correct / total:.0%}), {wrong} wrong, {no_answer} no answer")
    print(f"Total wall-clock time: {total_seconds:.1f}s ({total_seconds / total:.1f}s/song average)")
    if RETRY_EVENT_COUNTS:
        print(f"Retry/backoff events: {dict(RETRY_EVENT_COUNTS)}")
    for host, stats in response_time_summary().items():
        print(f"  {host}: {stats['count']} calls, avg {stats['average_seconds']:.2f}s")
