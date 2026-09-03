"""Run MusicBrainz, Discogs, and Wikidata across a spread of test songs
(mainstream / mid-tier / niche / Romanian), with each source's own pacing
enforced in code rather than left to manual care, see spikes/README.md.

Usage: python spikes/run_matrix.py
"""

import time

import discogs_spike
import musicbrainz_spike
import wikidata_spike
from _shared import DISCOGS_DELAY_SECONDS, MUSICBRAINZ_DELAY_SECONDS, WIKIDATA_DELAY_SECONDS, extract_year

# (title, artist, album, tier, note) - album is None when there's no separate
# album to compare against, or when guessing the title risks being wrong
SONGS = [
    ("Never Gonna Give You Up", "Rick Astley", "Whenever You Need Somebody", "mainstream", ""),
    ("Bohemian Rhapsody", "Queen", "A Night at the Opera", "mainstream", ""),
    ("Billie Jean", "Michael Jackson", "Thriller", "mainstream", ""),
    ("Windowlicker", "Aphex Twin", None, "mid", "released as its own EP, no separate parent album to compare"),
    ("Roygbiv", "Boards of Canada", "Music Has The Right To Children", "mid", ""),
    ("Such Great Heights", "The Postal Service", "Give Up", "mid", ""),
    ("リサフランク420 / 現代のコンピュー", "Macintosh Plus", "Floral Shoppe", "niche", "vaporwave cult release, real stylized title, not romanized"),
    ("Dragostea Din Tei", "O-Zone", "DiscO-Zone", "romanian", "Romanian-language, but an international hit; album title found via Wikidata's P361 link on the song entity, not guessed"),
]


def run_musicbrainz(title: str, artist: str, album: str | None) -> None:
    time.sleep(MUSICBRAINZ_DELAY_SECONDS)
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
            time.sleep(MUSICBRAINZ_DELAY_SECONDS)
            artist_data = musicbrainz_spike.get_artist(artist_id)
            area = (artist_data.get("area") or {}).get("name")
            country = artist_data.get("country")
            print(f"  MusicBrainz artist area: {area} (country code {country})")

    album_date = None
    if album:
        time.sleep(MUSICBRAINZ_DELAY_SECONDS)
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
        time.sleep(DISCOGS_DELAY_SECONDS)
        master = discogs_spike.get_master(master_id)
        year = discogs_spike.master_year(master)
        print(f"  Discogs ({label}) master {master_id}: year={year} title={master.get('title')!r}")
        if year is not None:
            master_years.append(year)

    if not master_years:
        return None
    return min(master_years)


def run_discogs(title: str, artist: str, album: str | None) -> None:
    time.sleep(DISCOGS_DELAY_SECONDS)
    track_year = _discogs_lookup(title, artist, "track")

    album_year = None
    if album:
        time.sleep(DISCOGS_DELAY_SECONDS)
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
    disambiguated = best is not top_ranked_match
    print(
        f"  Wikidata ({label} query): {len(matches)} match(es), picked={best['id']} ({best.get('description')})"
        f"{' [disambiguated away from top rank]' if disambiguated else ''}"
    )
    top_id = best["id"]
    time.sleep(WIKIDATA_DELAY_SECONDS)
    entity = wikidata_spike.get_entity(top_id)["entities"][top_id]
    date = wikidata_spike.extract_publication_date(entity)
    country_id = wikidata_spike.extract_entity_id_claim(entity, wikidata_spike.COUNTRY_OF_ORIGIN_PROPERTY)
    language_id = wikidata_spike.extract_entity_id_claim(entity, wikidata_spike.LANGUAGE_OF_WORK_PROPERTY)

    time.sleep(WIKIDATA_DELAY_SECONDS)
    labels = wikidata_spike.resolve_labels([entity_id for entity_id in (country_id, language_id) if entity_id])

    time.sleep(WIKIDATA_DELAY_SECONDS)
    sitelinks_count = wikidata_spike.get_sitelinks_count(top_id)

    print(f"  Wikidata ({label}) P577 (publication date): {date}")
    print(f"  Wikidata ({label}) P495 (country of origin): {labels.get(country_id, country_id)}")
    print(f"  Wikidata ({label}) P407 (language of work): {labels.get(language_id, language_id)}")
    print(f"  Wikidata ({label}) sitelinks (Wikipedia language editions): {sitelinks_count}")
    return date


def run_wikidata(title: str, artist: str, album: str | None) -> None:
    time.sleep(WIKIDATA_DELAY_SECONDS)
    track_date = _wikidata_lookup(title, artist, "track")

    album_date = None
    if album:
        time.sleep(WIKIDATA_DELAY_SECONDS)
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


if __name__ == "__main__":
    for title, artist, album, tier, note in SONGS:
        header = f"=== [{tier}] {title!r} by {artist!r}" + (f" (album: {album!r})" if album else "") + " ==="
        if note:
            header += f"  ({note})"
        print(header)
        run_musicbrainz(title, artist, album)
        run_discogs(title, artist, album)
        run_wikidata(title, artist, album)
        print()
