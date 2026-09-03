"""Run MusicBrainz, Discogs, and Wikidata across a spread of test songs
(mainstream / mid-tier / niche / Romanian), with each source's own pacing
enforced in code rather than left to manual care, see spikes/README.md.

Usage: python spikes/run_matrix.py
"""

import time

import discogs_spike
import musicbrainz_spike
import wikidata_spike

MUSICBRAINZ_DELAY_SECONDS = 2.5  # documented hard limit is 1 request/second; padded well above it
DISCOGS_DELAY_SECONDS = 1.1  # well under the 60/minute authenticated limit
WIKIDATA_DELAY_SECONDS = 0.5  # no published hard limit, still pace politely

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
    ("Dragostea Din Tei", "O-Zone", None, "romanian", "Romanian-language, but an international hit; not confident enough of the parent album's exact title to test it"),
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
            f"primary-type={best.get('primary-type')} tags={[t['name'] for t in best.get('tags', [])]}"
        )

        artist_id = artist_credit[0].get("artist", {}).get("id") if artist_credit else None
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
            agreement = "agrees" if album_date and track_date and album_date == track_date else "DIFFERS"
            print(
                f"  MusicBrainz (album query): selected first-release-date={album_date} "
                f"primary-type={album_best.get('primary-type')} [{agreement} with track query]"
            )

    dated_candidates = [d for d in (track_date, album_date) if d]
    if dated_candidates:
        print(f"  MusicBrainz FINAL (earliest of track/album): {min(dated_candidates)}")


def run_discogs(title: str, artist: str) -> None:
    time.sleep(DISCOGS_DELAY_SECONDS)
    results = discogs_spike.search_release(title, artist)
    releases = results.get("results", [])
    if not releases:
        print("  Discogs: no release match")
        return

    master_id = releases[0].get("master_id")
    print(f"  Discogs: {len(releases)} release(s) shown, top result year={releases[0].get('year')}")
    if master_id:
        time.sleep(DISCOGS_DELAY_SECONDS)
        master = discogs_spike.get_master(master_id)
        print(f"  Discogs master: year={master.get('year')} genres={master.get('genres')} styles={master.get('styles')}")


def _wikidata_lookup(title: str, artist: str, label: str) -> str | None:
    matches = wikidata_spike.search_entity(title).get("search", [])
    if not matches:
        print(f"  Wikidata ({label} query): no entity match")
        return None

    best = wikidata_spike.pick_best_match(matches, artist)
    disambiguated = best is not matches[0]
    print(
        f"  Wikidata ({label} query): {len(matches)} match(es), picked={best['id']} ({best.get('description')})"
        f"{' [disambiguated away from top rank]' if disambiguated else ''}"
    )
    top_id = best["id"]
    time.sleep(WIKIDATA_DELAY_SECONDS)
    entity = wikidata_spike.get_entity(top_id)["entities"][top_id]
    date = wikidata_spike.extract_publication_date(entity)
    country_id = wikidata_spike.extract_entity_id_claim(entity, "P495")
    language_id = wikidata_spike.extract_entity_id_claim(entity, "P407")

    time.sleep(WIKIDATA_DELAY_SECONDS)
    labels = wikidata_spike.resolve_labels([i for i in (country_id, language_id) if i])

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
            agreement = "agrees" if album_date == track_date else "DIFFERS"
            print(f"  Wikidata track vs. album query: [{agreement}]")

    dated_candidates = [d for d in (track_date, album_date) if d]
    if dated_candidates:
        print(f"  Wikidata FINAL (earliest of track/album): {min(dated_candidates)}")


if __name__ == "__main__":
    for title, artist, album, tier, note in SONGS:
        header = f"=== [{tier}] {title!r} by {artist!r}" + (f" (album: {album!r})" if album else "") + " ==="
        if note:
            header += f"  ({note})"
        print(header)
        run_musicbrainz(title, artist, album)
        run_discogs(title, artist)
        run_wikidata(title, artist, album)
        print()
