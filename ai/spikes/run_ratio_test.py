"""Runs MusicBrainz and Discogs (only, Wikidata excluded, see
ratio_test_songs.py) against the second test batch, timed and scored
against ground truth, to calibrate the fast-tier traffic-split ratio
between the two. See spikes/README.md.

Usage: python spikes/run_ratio_test.py
"""

import time

import discogs_spike
import musicbrainz_spike
from _shared import RETRY_EVENT_COUNTS, extract_year, response_time_summary
from ratio_test_songs import GROUND_TRUTH, RATIO_TEST_SONGS


def run_musicbrainz(title: str, artist: str, album: str | None) -> int | None:
    data = musicbrainz_spike.search_release_group(title, artist)
    groups = data.get("release-groups", [])
    track_date = None
    if groups:
        track_date = musicbrainz_spike.select_best_release_group(groups).get("first-release-date")

    album_date = None
    if album:
        album_data = musicbrainz_spike.search_release_group(album, artist)
        album_groups = album_data.get("release-groups", [])
        if album_groups:
            album_date = musicbrainz_spike.select_best_release_group(album_groups, prefer_type="Album").get(
                "first-release-date"
            )

    years = [extract_year(date_value) for date_value in (track_date, album_date) if extract_year(date_value)]
    return min(years) if years else None


def _discogs_lookup(title: str, artist: str) -> int | None:
    results = discogs_spike.search_release(title, artist)
    releases = results.get("results", [])
    if not releases:
        return None
    master_ids = discogs_spike.find_master_ids(releases)
    if not master_ids:
        return None
    years = []
    for master_id in master_ids:
        year = discogs_spike.master_year(discogs_spike.get_master(master_id))
        if year is not None:
            years.append(year)
    return min(years) if years else None


def run_discogs(title: str, artist: str, album: str | None) -> int | None:
    track_year = _discogs_lookup(title, artist)
    album_year = _discogs_lookup(album, artist) if album else None
    years = [year for year in (track_year, album_year) if year is not None]
    return min(years) if years else None


if __name__ == "__main__":
    run_started_at = time.perf_counter()
    source_seconds = {"MusicBrainz": 0.0, "Discogs": 0.0}
    musicbrainz_correct = musicbrainz_no_answer = 0
    discogs_correct = discogs_no_answer = 0

    for song_index, (title, artist, album, tier, note) in enumerate(RATIO_TEST_SONGS, start=1):
        acceptable_years = GROUND_TRUTH[title]
        print(f"=== [{song_index}/{len(RATIO_TEST_SONGS)}] [{tier}] {title!r} by {artist!r} ===")

        started_at = time.perf_counter()
        musicbrainz_year = run_musicbrainz(title, artist, album)
        source_seconds["MusicBrainz"] += time.perf_counter() - started_at
        musicbrainz_outcome = (
            "no answer"
            if musicbrainz_year is None
            else ("correct" if musicbrainz_year in acceptable_years else f"WRONG ({musicbrainz_year})")
        )
        if musicbrainz_year is None:
            musicbrainz_no_answer += 1
        elif musicbrainz_year in acceptable_years:
            musicbrainz_correct += 1
        print(f"  MusicBrainz: {musicbrainz_year} [{musicbrainz_outcome}]")

        started_at = time.perf_counter()
        discogs_year = run_discogs(title, artist, album)
        source_seconds["Discogs"] += time.perf_counter() - started_at
        discogs_outcome = "no answer" if discogs_year is None else ("correct" if discogs_year in acceptable_years else f"WRONG ({discogs_year})")
        if discogs_year is None:
            discogs_no_answer += 1
        elif discogs_year in acceptable_years:
            discogs_correct += 1
        print(f"  Discogs: {discogs_year} [{discogs_outcome}]")
        print()

    total = len(RATIO_TEST_SONGS)
    total_seconds = time.perf_counter() - run_started_at
    print("=== Ratio test results (this batch only) ===")
    print(f"Total wall-clock time: {total_seconds:.1f}s for {total} songs")
    print(f"  MusicBrainz: {source_seconds['MusicBrainz']:.1f}s total, {musicbrainz_correct}/{total} correct ({musicbrainz_correct/total:.0%}), {musicbrainz_no_answer}/{total} no answer")
    print(f"  Discogs: {source_seconds['Discogs']:.1f}s total, {discogs_correct}/{total} correct ({discogs_correct/total:.0%}), {discogs_no_answer}/{total} no answer")
    if RETRY_EVENT_COUNTS:
        print(f"Retry/backoff events: {dict(RETRY_EVENT_COUNTS)}")
    print("\n=== Pure response latency ===")
    for host, stats in response_time_summary().items():
        print(f"  {host}: {stats['count']} calls, avg {stats['average_seconds']:.2f}s")
