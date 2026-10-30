"""Records what every metadata source answers for each benchmark song, so year decisions
can be measured offline against known release years without querying the sources again.

Usage, from ai/ (one process per source, so a slow source does not hold the others up):
    .venv/Scripts/python.exe -m benchmark.capture --source musicbrainz
    .venv/Scripts/python.exe -m benchmark.capture --source discogs --refresh
    .venv/Scripts/python.exe -m benchmark.capture --source wikipedia --songs benchmark/catalog.csv

Each answer is written to benchmark/captures/<source>/<slug>.json. Queries the live sources
and the extraction model, so keys from ai/.env are needed.
"""
import argparse
import csv
import json
import re
import unicodedata
from pathlib import Path

from app.metadata import service
from app.metadata.verification import _run_wikipedia_extraction

BENCHMARK_DIRECTORY = Path(__file__).parent
CAPTURE_DIRECTORY = BENCHMARK_DIRECTORY / "captures"
DEFAULT_SONGS_FILE = BENCHMARK_DIRECTORY / "songs.csv"
STRUCTURED_SOURCES = ("musicbrainz", "discogs", "wikidata")
WIKIPEDIA_SOURCE = "wikipedia"
SOURCES = (*STRUCTURED_SOURCES, WIKIPEDIA_SOURCE)
ARTIST_SEPARATOR = "|"


def slug(title: str, artists: list[str]) -> str:
    text = unicodedata.normalize("NFKD", f"{artists[0] if artists else ''}-{title}")
    return re.sub(r"[^a-z0-9]+", "-", text.encode("ascii", "ignore").decode().lower()).strip("-")


def read_songs(songs_file: Path) -> list[dict]:
    with songs_file.open(encoding="utf8", newline="") as handle:
        return [
            {
                "title": row["title"],
                "main_artists": [artist.strip() for artist in row["main_artists"].split(ARTIST_SEPARATOR) if artist.strip()],
                "expected_year": int(row["expected_year"]) if row.get("expected_year") else None,
                "note": row.get("note") or "",
            }
            for row in csv.DictReader(handle)
        ]


def answer(source: str, title: str, artists: list[str]) -> object:
    if source == WIKIPEDIA_SOURCE:
        entries = service.run_wikipedia_stage(title, artists)
        extraction = _run_wikipedia_extraction(title, " & ".join(artists), entries)
        return {
            "pages": [entry.get("page_title") for entry in entries],
            "year": extraction.release_year if extraction else None,
            "confidence": extraction.confidence if extraction else None,
        }
    return service.run_structured_source_stage(source, title, artists).candidates


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", choices=SOURCES, required=True)
    parser.add_argument("--songs", type=Path, default=DEFAULT_SONGS_FILE)
    parser.add_argument("--refresh", action="store_true")
    arguments = parser.parse_args()
    directory = CAPTURE_DIRECTORY / arguments.source
    directory.mkdir(parents=True, exist_ok=True)

    for song in read_songs(arguments.songs):
        path = directory / f"{slug(song['title'], song['main_artists'])}.json"
        if path.exists() and not arguments.refresh:
            continue
        path.write_text(json.dumps(answer(arguments.source, song["title"], song["main_artists"]), ensure_ascii=False), encoding="utf8")
        print(f"{arguments.source}: {path.name}", flush=True)


if __name__ == "__main__":
    main()
