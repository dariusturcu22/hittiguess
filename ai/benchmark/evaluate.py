"""Replays captured source answers through year-decision rules and scores each rule
against the benchmark's known release years. A rule returns a year to lock as VERIFIED,
or None when it would leave the song to reconciliation and review.

Usage, from ai/: .venv/Scripts/python.exe -m benchmark.evaluate [--misses RULE]
"""
import argparse
import json
from collections import Counter
from pathlib import Path

from benchmark.capture import DEFAULT_SONGS_FILE, SOURCES, read_songs, slug
from app.metadata import verification
from app.metadata.verification import _all_three_agree, _extract_earliest_year

CAPTURE_DIRECTORY = Path(__file__).parent / "captures"
MINIMUM_SUPPORTING_SOURCES = 2


def load_captures(songs_file: Path) -> list[dict]:
    """Songs that every source has answered, with the answers keyed by source."""
    captures = []
    for song in read_songs(songs_file):
        name = f"{slug(song['title'], song['main_artists'])}.json"
        paths = {source: CAPTURE_DIRECTORY / source / name for source in SOURCES}
        if all(path.exists() for path in paths.values()):
            captures.append({**song, **{source: json.loads(path.read_text(encoding="utf8")) for source, path in paths.items()}})
    return captures


def year_of(candidate: dict) -> int | None:
    return _extract_earliest_year([candidate])


def evidence(capture: dict) -> dict:
    """Each source's earliest year, with Wikidata's track and album dates kept apart."""
    wikidata = capture.get("wikidata") or []
    return {
        "musicbrainz": _extract_earliest_year(capture.get("musicbrainz") or []),
        "discogs": _extract_earliest_year(capture.get("discogs") or []),
        "wikidata_track": _extract_earliest_year([c for c in wikidata if c.get("query") == "track"]),
        "wikidata_album": _extract_earliest_year([c for c in wikidata if c.get("query") == "album"]),
        "wikipedia": (capture.get("wikipedia") or {}).get("year"),
    }


def task_15_rule(found: dict) -> int | None:
    """The rule from task 15, kept for comparison: three equal years, or a one-year
    cluster taking its earliest year when two sources report it, else the most reported."""
    wikidata = min([year for year in (found["wikidata_track"], found["wikidata_album"]) if year], default=None)
    if _all_three_agree(found["musicbrainz"], found["discogs"], wikidata):
        return found["musicbrainz"]
    years = [year for year in (found["musicbrainz"], found["discogs"], wikidata, found["wikipedia"]) if year]
    if len(years) < 3:
        return None
    counts = Counter(years)
    for year, count in counts.items():
        if count >= 3:
            return year
    if max(years) - min(years) <= 1:
        if counts[min(years)] >= 2:
            return min(years)
        return max(sorted(counts), key=counts.get)
    return None


def production_rule(capture: dict, found: dict) -> int | None:
    """verification.evaluate_lock without its model calls: the exact-agreement lock, then
    the earliest year two sources support."""
    wikidata = _extract_earliest_year(capture["wikidata"] or [])
    if _all_three_agree(found["musicbrainz"], found["discogs"], wikidata):
        return found["musicbrainz"]
    return verification._earliest_supported_year(found["musicbrainz"], found["discogs"], capture["wikidata"] or [], found["wikipedia"])


def earliest_supported(years: list[int | None]) -> int | None:
    """The earliest year that at least two sources report. A later year never contradicts an
    earlier release; only an earlier year needs a second source to count."""
    counts = Counter(year for year in years if year)
    supported = [year for year, count in counts.items() if count >= MINIMUM_SUPPORTING_SOURCES]
    return min(supported) if supported else None


def earliest_supported_with_album(found: dict) -> int | None:
    wikidata = min([year for year in (found["wikidata_track"], found["wikidata_album"]) if year], default=None)
    return earliest_supported([found["musicbrainz"], found["discogs"], wikidata, found["wikipedia"]])


def earliest_supported_track_only(found: dict) -> int | None:
    return earliest_supported([found["musicbrainz"], found["discogs"], found["wikidata_track"], found["wikipedia"]])


def earliest_supported_album_as_vote(found: dict) -> int | None:
    """Wikidata's track and album dates as two separate votes."""
    return earliest_supported([found["musicbrainz"], found["discogs"], found["wikidata_track"],
                               found["wikidata_album"], found["wikipedia"]])


def earliest_supported_album_confirmed(found: dict) -> int | None:
    """Wikidata's album date is the original edition's, so it votes only when MusicBrainz,
    which checks the editions that contain the recording, reports the same year."""
    album_vote = found["wikidata_album"] if found["wikidata_album"] == found["musicbrainz"] else None
    return earliest_supported([found["musicbrainz"], found["discogs"], found["wikidata_track"],
                               album_vote, found["wikipedia"]])


RULES = {
    "task_15": task_15_rule,
    "earliest_supported_with_album": earliest_supported_with_album,
    "earliest_supported_track_only": earliest_supported_track_only,
    "earliest_supported_album_as_vote": earliest_supported_album_as_vote,
    "earliest_supported_album_confirmed": earliest_supported_album_confirmed,
    "production": production_rule,
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--misses", choices=list(RULES), action="append", default=[])
    parser.add_argument("--songs", type=Path, default=DEFAULT_SONGS_FILE)
    parser.add_argument("--unlabelled", action="store_true", help="print the production rule's year for songs without a known year")
    arguments = parser.parse_args()
    captures = load_captures(arguments.songs)
    labelled = [capture for capture in captures if capture.get("expected_year")]

    for name, rule in RULES.items():
        outcome = Counter()
        misses = []
        for capture in labelled:
            found = evidence(capture)
            decided = rule(capture, found) if rule is production_rule else rule(found)
            if decided is None:
                outcome["unresolved"] += 1
                misses.append(("unresolved", capture, found, decided))
            elif decided == capture["expected_year"]:
                outcome["correct"] += 1
            else:
                outcome["wrong"] += 1
                misses.append(("WRONG", capture, found, decided))
        print(f"{name:36} correct {outcome['correct']:3}  wrong {outcome['wrong']:3}  unresolved {outcome['unresolved']:3}  of {len(labelled)}")
        if name in arguments.misses:
            for kind, capture, found, decided in misses:
                print(f"    {kind:10} {capture['title']} ({', '.join(capture['main_artists'])}) expected {capture['expected_year']} got {decided} {found}")

    if arguments.unlabelled:
        for capture in captures:
            if capture.get("expected_year"):
                continue
            found = evidence(capture)
            print(f"    unlabelled {capture['title']} ({', '.join(capture['main_artists'])}) locks {production_rule(capture, found)} {found}")


if __name__ == "__main__":
    main()
