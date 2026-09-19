"""Runs the four-source combination (MusicBrainz + Discogs + Wikidata +
Wikipedia) through gpt-5-nano's reconciliation, all 49 songs. Wikipedia's
contribution is DeepSeek-V4-Flash's already-extracted year (see
run_wikipedia_extraction_test.py's saved results), not raw article prose,
keeping the extraction and reconciliation steps on separate models as
decided. Reads all four sources from their cached data
(response_cache.py), no live API calls. See spikes/README.md.

Usage: python spikes/run_four_source_combo.py
"""

import json
import time
from pathlib import Path

import response_cache
from all_songs import ALL_SONGS, GROUND_TRUTH
from combo_prompts import build_four_sources_prompt
from openai_compatible_spike import extract

RESULTS_DIR = Path(__file__).resolve().parent
WIKIPEDIA_EXTRACTION_MODEL = "deepseek-ai/DeepSeek-V4-Flash"
PROVIDER = "openai"
MODEL = "gpt-5-nano"


def _load_wikipedia_extractions() -> dict[str, dict]:
    results = json.loads(
        (RESULTS_DIR / ".llm_combo_results_wikipedia_extraction.json").read_text(encoding="utf-8")
    )
    deepseek_result = next(entry for entry in results if entry["model"] == WIKIPEDIA_EXTRACTION_MODEL)
    return {song["title"]: song for song in deepseek_result["per_song"]}


if __name__ == "__main__":
    wikipedia_extractions = _load_wikipedia_extractions()

    run_started_at = time.perf_counter()
    correct = wrong = no_answer = 0
    per_song_results = []

    for song_index, (title, artist, album, tier, note) in enumerate(ALL_SONGS, start=1):
        acceptable_years = GROUND_TRUTH[title]
        musicbrainz_candidates = response_cache.load("musicbrainz", title, artist) or []
        discogs_candidates = response_cache.load("discogs", title, artist) or []
        wikidata_candidates = response_cache.load("wikidata", title, artist) or []
        wikipedia_entry = wikipedia_extractions.get(title, {})
        wikipedia_year = wikipedia_entry.get("release_year")
        wikipedia_confidence = wikipedia_entry.get("confidence")

        prompt = build_four_sources_prompt(
            title, artist, musicbrainz_candidates, discogs_candidates, wikidata_candidates,
            wikipedia_year, wikipedia_confidence,
        )
        try:
            result = extract(PROVIDER, MODEL, prompt)
            release_year = result.release_year
        except Exception as error:
            print(f"[{song_index}/{len(ALL_SONGS)}] {title!r}: ERROR ({error})")
            per_song_results.append({"title": title, "artist": artist, "outcome": "error", "error": str(error)})
            no_answer += 1
            continue

        if release_year is None:
            outcome = "no_answer"
            no_answer += 1
        elif release_year in acceptable_years:
            outcome = "correct"
            correct += 1
        else:
            outcome = "wrong"
            wrong += 1
        per_song_results.append(
            {
                "title": title,
                "artist": artist,
                "release_year": release_year,
                "acceptable_years": list(acceptable_years),
                "outcome": outcome,
            }
        )
        print(f"[{song_index}/{len(ALL_SONGS)}] [{tier}] {title!r}: {release_year} [{outcome}]")

    total = len(ALL_SONGS)
    total_seconds = time.perf_counter() - run_started_at
    output_path = RESULTS_DIR / ".llm_combo_results_four_source.json"
    output_path.write_text(
        json.dumps(
            {
                "provider": PROVIDER,
                "model": MODEL,
                "correct": correct,
                "wrong": wrong,
                "no_answer": no_answer,
                "total": total,
                "accuracy": correct / total,
                "per_song": per_song_results,
            },
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    print("\n=== Four-source combination (gpt-5-nano) results ===")
    print(f"{correct}/{total} correct ({correct / total:.0%}), {wrong} wrong, {no_answer} no answer")
    print(f"Total wall-clock time: {total_seconds:.1f}s")
    print(f"Saved to {output_path}")
    print("\nFor comparison, the three-source (no Wikipedia) combo scored 45/49 (92%) with gpt-5-nano.")
