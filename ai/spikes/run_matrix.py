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

# (title, artist, tier, note)
SONGS = [
    ("Never Gonna Give You Up", "Rick Astley", "mainstream", ""),
    ("Bohemian Rhapsody", "Queen", "mainstream", ""),
    ("Windowlicker", "Aphex Twin", "mid", ""),
    ("Roygbiv", "Boards of Canada", "mid", ""),
    ("Rissafuranku 420 Gendai no Kompyu", "Macintosh Plus", "niche", "vaporwave cult release, title is stylized in Japanese"),
    ("Dragostea Din Tei", "O-Zone", "romanian", "Romanian-language, but an international hit"),
]


def run_musicbrainz(title: str, artist: str) -> None:
    time.sleep(MUSICBRAINZ_DELAY_SECONDS)
    data = musicbrainz_spike.search_release_group(title, artist)
    groups = data.get("release-groups", [])
    if not groups:
        print("  MusicBrainz: no release-group match")
        return

    top = groups[0]
    artist_credit = top.get("artist-credit", [{}])
    print(
        f"  MusicBrainz: score={top.get('score')} first-release-date={top.get('first-release-date')} "
        f"tags={[t['name'] for t in top.get('tags', [])]}"
    )

    artist_id = artist_credit[0].get("artist", {}).get("id") if artist_credit else None
    if artist_id:
        time.sleep(MUSICBRAINZ_DELAY_SECONDS)
        artist_data = musicbrainz_spike.get_artist(artist_id)
        area = (artist_data.get("area") or {}).get("name")
        country = artist_data.get("country")
        print(f"  MusicBrainz artist area: {area} (country code {country})")


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


def run_wikidata(title: str) -> None:
    time.sleep(WIKIDATA_DELAY_SECONDS)
    matches = wikidata_spike.search_entity(title).get("search", [])
    if not matches:
        print("  Wikidata: no entity match")
        return

    print(f"  Wikidata: {len(matches)} match(es), top={matches[0]['id']} ({matches[0].get('description')})")
    top_id = matches[0]["id"]
    time.sleep(WIKIDATA_DELAY_SECONDS)
    entity = wikidata_spike.get_entity(top_id)["entities"][top_id]
    date = wikidata_spike.extract_publication_date(entity)
    country_id = wikidata_spike.extract_entity_id_claim(entity, "P495")
    language_id = wikidata_spike.extract_entity_id_claim(entity, "P407")

    time.sleep(WIKIDATA_DELAY_SECONDS)
    labels = wikidata_spike.resolve_labels([i for i in (country_id, language_id) if i])
    print(f"  Wikidata P577 (publication date): {date}")
    print(f"  Wikidata P495 (country of origin): {labels.get(country_id, country_id)}")
    print(f"  Wikidata P407 (language of work): {labels.get(language_id, language_id)}")


if __name__ == "__main__":
    for title, artist, tier, note in SONGS:
        header = f"=== [{tier}] {title!r} by {artist!r} ==="
        if note:
            header += f"  ({note})"
        print(header)
        run_musicbrainz(title, artist)
        run_discogs(title, artist)
        run_wikidata(title)
        print()
