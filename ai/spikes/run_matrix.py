"""Run MusicBrainz, Discogs, and Wikidata across a spread of test songs
(mainstream / mid-tier / niche / Romanian), with each source's own pacing
enforced in code rather than left to manual care, see spikes/README.md.

Usage: python spikes/run_matrix.py
"""

import time

import discogs_spike
import musicbrainz_spike
import wikidata_spike
from _shared import RETRY_EVENT_COUNTS, extract_year, response_time_summary

# (title, artist, album, tier, note) - album is None when there's no separate
# album to compare against, or when guessing the title risks being wrong
#
# Kept from the original agreement-heavy matrix as a small sanity baseline
# (confirm sources aren't inventing numbers), the rest is adversarial:
# genuine reissue/pressing disagreement, thin/partial coverage, cover-version
# attribution risk, and title collisions that could pull the wrong candidate
# during MusicBrainz/Wikidata disambiguation. Every entry is a real song with
# a documented reason to expect friction, not invented; which ones actually
# produce disagreement or gaps gets confirmed by running this script, not
# assumed from the note alone.
SONGS = [
    # --- sanity baseline: expect agreement, catch outright invention ---
    ("Never Gonna Give You Up", "Rick Astley", "Whenever You Need Somebody", "sanity", ""),
    ("Bohemian Rhapsody", "Queen", "A Night at the Opera", "sanity", ""),
    ("リサフランク420 / 現代のコンピュー", "Macintosh Plus", "Floral Shoppe", "sanity", "vaporwave cult release, real stylized title, not romanized"),
    ("Dragostea Din Tei", "O-Zone", "DiscO-Zone", "sanity", "Romanian-language, but an international hit; album title found via Wikidata's P361 link on the song entity"),

    # --- reissue / multiple-pressing history: real risk of disagreeing "first release" years ---
    ("Blue Monday", "New Order", None, "reissue", "1983 12\" single, deliberately never on the contemporaneous studio album; multiple distinct CD reissues since"),
    ("Take On Me", "a-ha", "Hunting High and Low", "reissue", "a materially different 1984 original single release predates the famous 1985 re-release with the animated video"),
    ("I Melt With You", "Modern English", "After the Snow", "reissue", "1982 original recording versus a distinct 1990 re-recording that charted separately"),
    ("Tainted Love", "Soft Cell", "Non-Stop Erotic Cabaret", "reissue", "1981 hit is a cover; original Gloria Jones recording is 1964, risk of a source picking the wrong era"),
    ("I Will Survive", "Gloria Gaynor", "Love Tracks", "reissue", "started as a B-side before being promoted to the A-side, murky initial release dating"),
    ("Plastic Love", "Mariya Takeuchi", "Variety", "reissue", "1984 Japan-only release, went viral internationally decades later; risk of a source dating it to the resurgence instead"),

    # --- cover-version attribution risk: contamination from the more search-popular original ---
    ("Hurt", "Johnny Cash", "American IV: The Man Comes Back", "cover-attribution", "2002 cover; Nine Inch Nails' 1994 original is far more search-prominent"),
    ("I Fought the Law", "The Clash", "The Clash (US)", "cover-attribution", "1979 cover; The Bobby Fuller Four's 1966 original is the more commonly indexed version"),

    # --- thin / partial coverage: expect at least one source to come up empty ---
    ("Alpha and Omega", "Boards of Canada", None, "partial-coverage", "promo-only release, thinner catalog presence than a standard single"),
    ("Palm Mall", "猫 シ Corp", None, "partial-coverage", "self-released vaporwave, minimal label-driven metadata trail"),
    ("Solar Will", "Enslaved", "Mið", "partial-coverage", "single released only days before the parent album's 2026-10-30 release; sources may not have caught up yet"),
    ("New Religion", "Bebe Rexha", None, "partial-coverage", "a feat.-credit single released the same week as this test; artist string deliberately excludes Faithless to test how a partial credit is handled"),

    # --- title collision: same title as a different, unrelated work ---
    ("Style", "Taylor Swift", "1989", "title-collision", "title shared with unrelated songs by other artists"),
    ("Yesterday", "The Beatles", "Help!", "title-collision", "one of the most covered song titles in existence"),
    ("Closer", "Nine Inch Nails", "The Downward Spiral", "title-collision", "title shared with The Chainsmokers' 2016 song, a wildly different genre and era"),

    # --- extraction ambiguity: multi-artist or remix credits in the query itself ---
    ("Under Pressure", "Queen & David Bowie", "Hot Space", "extraction-ambiguity", "dual-artist credit as a single string, tests whether a source splits or mishandles it"),
    ("Say So", "Doja Cat", "Hot Pink", "extraction-ambiguity", "widely known via a Nicki Minaj remix version, tests whether a source conflates the remix with the original"),
]


def run_musicbrainz(title: str, artist: str, album: str | None) -> None:
    data = musicbrainz_spike.search_release_group(title, artist)
    groups = data.get("release-groups", [])
    track_date = None
    if not groups:
        print("  MusicBrainz (track query): no release-group match")
    else:
        best = musicbrainz_spike.select_best_release_group(groups)
        track_date = best.get("first-release-date")
        artist_credit = best.get("artist-credit", [{}])
        print(
            f"  MusicBrainz (track query): {len(groups)} candidate(s), selected first-release-date={track_date} "
            f"primary-type={best.get('primary-type')} tags={[tag['name'] for tag in best.get('tags', [])]}"
        )

        primary_artist_credit = artist_credit[0] if artist_credit else None
        artist_id = primary_artist_credit.get("artist", {}).get("id") if primary_artist_credit else None
        if artist_id:
            artist_data = musicbrainz_spike.get_artist(artist_id)
            area = (artist_data.get("area") or {}).get("name")
            country = artist_data.get("country")
            print(f"  MusicBrainz artist area: {area} (country code {country})")

    album_date = None
    if album:
        album_data = musicbrainz_spike.search_release_group(album, artist)
        album_groups = album_data.get("release-groups", [])
        if not album_groups:
            print("  MusicBrainz (album query): no release-group match")
        else:
            album_best = musicbrainz_spike.select_best_release_group(album_groups, prefer_type="Album")
            album_date = album_best.get("first-release-date")
            agreement = "agrees" if extract_year(album_date) == extract_year(track_date) else "DIFFERS"
            print(
                f"  MusicBrainz (album query): selected first-release-date={album_date} "
                f"primary-type={album_best.get('primary-type')} [{agreement} with track query]"
            )

    date_by_year: dict[int, str] = {}
    for date_value in (track_date, album_date):
        year_value = extract_year(date_value)
        if year_value is not None:
            date_by_year[year_value] = date_value
    if date_by_year:
        earliest_year = min(date_by_year)
        print(
            f"  MusicBrainz FINAL year (earliest of track/album): {earliest_year} "
            f"(from {date_by_year[earliest_year]!r})"
        )


def _discogs_lookup(title: str, artist: str, label: str) -> int | None:
    results = discogs_spike.search_release(title, artist)
    releases = results.get("results", [])
    if not releases:
        print(f"  Discogs ({label} query): no release match")
        return None

    master_ids = discogs_spike.find_master_ids(releases)
    top_release = releases[0]
    print(
        f"  Discogs ({label} query): {len(releases)} release(s) shown, top result year={top_release.get('year')}, "
        f"master_ids={master_ids}"
    )
    if not master_ids:
        return None

    master_years = []
    for master_id in master_ids:
        master = discogs_spike.get_master(master_id)
        year = discogs_spike.master_year(master)
        print(f"  Discogs ({label}) master {master_id}: year={year} title={master.get('title')!r}")
        if year is not None:
            master_years.append(year)

    if not master_years:
        return None
    return min(master_years)


def run_discogs(title: str, artist: str, album: str | None) -> None:
    track_year = _discogs_lookup(title, artist, "track")

    album_year = None
    if album:
        album_year = _discogs_lookup(album, artist, "album")
        if album_year and track_year:
            agreement = "agrees" if album_year == track_year else "DIFFERS"
            print(f"  Discogs track vs. album query: [{agreement}]")

    known_years = [year_value for year_value in (track_year, album_year) if year_value is not None]
    if known_years:
        print(f"  Discogs FINAL year (earliest of track/album): {min(known_years)}")


def _wikidata_lookup(title: str, artist: str, label: str) -> str | None:
    matches = wikidata_spike.search_entity(title).get("search", [])
    if not matches:
        print(f"  Wikidata ({label} query): no entity match")
        return None

    top_ranked_match = matches[0]
    best = wikidata_spike.pick_best_match(matches, artist)
    if best is None:
        print(f"  Wikidata ({label} query): {len(matches)} match(es), none confidently tied to {artist!r}")
        return None
    disambiguated = best is not top_ranked_match
    print(
        f"  Wikidata ({label} query): {len(matches)} match(es), picked={best['id']} ({best.get('description')})"
        f"{' [disambiguated away from top rank]' if disambiguated else ''}"
    )
    top_id = best["id"]
    entity = wikidata_spike.get_entity(top_id)["entities"][top_id]
    date = wikidata_spike.extract_publication_date(entity)
    country_id = wikidata_spike.extract_entity_id_claim(entity, wikidata_spike.COUNTRY_OF_ORIGIN_PROPERTY)
    language_id = wikidata_spike.extract_entity_id_claim(entity, wikidata_spike.LANGUAGE_OF_WORK_PROPERTY)

    labels = wikidata_spike.resolve_labels([entity_id for entity_id in (country_id, language_id) if entity_id])

    sitelinks_count = wikidata_spike.get_sitelinks_count(top_id)

    print(f"  Wikidata ({label}) P577 (publication date): {date}")
    print(f"  Wikidata ({label}) P495 (country of origin): {labels.get(country_id, country_id)}")
    print(f"  Wikidata ({label}) P407 (language of work): {labels.get(language_id, language_id)}")
    print(f"  Wikidata ({label}) sitelinks (Wikipedia language editions): {sitelinks_count}")
    return date


def run_wikidata(title: str, artist: str, album: str | None) -> None:
    track_date = _wikidata_lookup(title, artist, "track")

    album_date = None
    if album:
        album_date = _wikidata_lookup(album, artist, "album")
        if album_date and track_date:
            agreement = "agrees" if extract_year(album_date) == extract_year(track_date) else "DIFFERS"
            print(f"  Wikidata track vs. album query: [{agreement}]")

    date_by_year: dict[int, str] = {}
    for date_value in (track_date, album_date):
        year_value = extract_year(date_value)
        if year_value is not None:
            date_by_year[year_value] = date_value
    if date_by_year:
        earliest_year = min(date_by_year)
        print(
            f"  Wikidata FINAL year (earliest of track/album): {earliest_year} (from {date_by_year[earliest_year]!r})"
        )


SOURCE_RUNNERS = {"MusicBrainz": run_musicbrainz, "Discogs": run_discogs, "Wikidata": run_wikidata}


SONG_LABEL_COLUMN_WIDTH = 40
TIMING_COLUMN_WIDTH = 14


if __name__ == "__main__":
    run_started_at = time.perf_counter()
    source_seconds: dict[str, float] = {source_name: 0.0 for source_name in SOURCE_RUNNERS}
    per_song_timings: list[tuple[str, dict[str, float]]] = []

    for song_index, (title, artist, album, tier, note) in enumerate(SONGS, start=1):
        header = f"=== [{song_index}/{len(SONGS)}] [{tier}] {title!r} by {artist!r}" + (
            f" (album: {album!r})" if album else ""
        ) + " ==="
        if note:
            header += f"  ({note})"
        print(header)
        song_source_seconds: dict[str, float] = {}
        for source_name, runner in SOURCE_RUNNERS.items():
            source_started_at = time.perf_counter()
            runner(title, artist, album)
            elapsed_seconds = time.perf_counter() - source_started_at
            song_source_seconds[source_name] = elapsed_seconds
            source_seconds[source_name] += elapsed_seconds
        per_song_timings.append((f"{title} / {artist}", song_source_seconds))
        print()

    total_seconds = time.perf_counter() - run_started_at
    print("=== Per-song timing (seconds) ===")
    header_row = f"{'Song':<{SONG_LABEL_COLUMN_WIDTH}}" + "".join(
        f"{source_name:>{TIMING_COLUMN_WIDTH}}" for source_name in SOURCE_RUNNERS
    ) + f"{'Total':>{TIMING_COLUMN_WIDTH}}"
    print(header_row)
    for song_label, timings in per_song_timings:
        truncated_label = song_label[:SONG_LABEL_COLUMN_WIDTH]
        row_total = sum(timings.values())
        row = f"{truncated_label:<{SONG_LABEL_COLUMN_WIDTH}}" + "".join(
            f"{timings[source_name]:>{TIMING_COLUMN_WIDTH}.1f}" for source_name in SOURCE_RUNNERS
        ) + f"{row_total:>{TIMING_COLUMN_WIDTH}.1f}"
        print(row)

    print("\n=== Run stats ===")
    print(f"Total wall-clock time: {total_seconds:.1f}s for {len(SONGS)} songs ({total_seconds / len(SONGS):.1f}s/song average)")
    for source_name, seconds_spent in source_seconds.items():
        print(f"  {source_name}: {seconds_spent:.1f}s total, {seconds_spent / len(SONGS):.1f}s/song average (wall time: pacing sleep + retries + actual response)")
    if RETRY_EVENT_COUNTS:
        print(f"Retry/backoff events: {dict(RETRY_EVENT_COUNTS)}")
    else:
        print("Retry/backoff events: none")

    print("\n=== Pure response latency (excludes pacing sleep and retry backoff) ===")
    for host, stats in response_time_summary().items():
        print(
            f"  {host}: {stats['count']} calls, avg {stats['average_seconds']:.2f}s, "
            f"min {stats['min_seconds']:.2f}s, max {stats['max_seconds']:.2f}s"
        )
