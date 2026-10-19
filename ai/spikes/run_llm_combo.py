"""Runs one LLM-combination scenario (LLM+MusicBrainz only, LLM+Discogs
only, or all three sources combined) across all four remaining LLM
candidates and all 49 songs, reading each source's data from
response_cache.py rather than making a live source-API call. Saves
results to a gitignored JSON file for the final four-way rollup.

The "musicbrainz" and "discogs" scenarios only need their own source's
cache populated; the "all" scenario needs all three, and per the agreed
sequencing shouldn't run until run_full_musicbrainz.py,
run_full_discogs.py, and run_full_wikidata.py have all finished. See
spikes/README.md.

Usage: python spikes/run_llm_combo.py <musicbrainz|discogs|all>
"""

import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import response_cache
from all_songs import ALL_SONGS, GROUND_TRUTH
from combo_prompts import build_all_sources_prompt, build_discogs_only_prompt, build_musicbrainz_only_prompt
from openai_compatible_spike import extract

RESULTS_DIR = Path(__file__).resolve().parent

LLM_CANDIDATES = [
    ("openai", "gpt-5-nano"),
    ("openai", "gpt-5-mini"),
    ("groq", "openai/gpt-oss-20b"),
    ("deepinfra", "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo"),
]


def build_prompt(scenario: str, title: str, artist: str) -> str:
    if scenario == "musicbrainz":
        candidates = response_cache.load("musicbrainz", title, artist) or []
        return build_musicbrainz_only_prompt(title, artist, candidates)
    if scenario == "discogs":
        candidates = response_cache.load("discogs", title, artist) or []
        return build_discogs_only_prompt(title, artist, candidates)
    if scenario == "all":
        musicbrainz_candidates = response_cache.load("musicbrainz", title, artist) or []
        discogs_candidates = response_cache.load("discogs", title, artist) or []
        wikidata_candidates = response_cache.load("wikidata", title, artist) or []
        return build_all_sources_prompt(
            title, artist, musicbrainz_candidates, discogs_candidates, wikidata_candidates
        )
    raise ValueError(f"unknown scenario: {scenario}")


def run_candidate(scenario: str, provider: str, model: str) -> dict:
    per_song_results = []
    correct = wrong = no_answer = 0
    for title, artist, album, tier, note in ALL_SONGS:
        acceptable_years = GROUND_TRUTH[title]
        prompt = build_prompt(scenario, title, artist)
        try:
            result = extract(provider, model, prompt)
            release_year = result.release_year
            confidence = result.confidence
        except Exception as error:
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
                "confidence": confidence,
                "outcome": outcome,
            }
        )
        print(f"    [{provider}/{model}] {title!r}: {release_year} [{outcome}]")

    total = len(ALL_SONGS)
    return {
        "provider": provider,
        "model": model,
        "correct": correct,
        "wrong": wrong,
        "no_answer": no_answer,
        "total": total,
        "accuracy": correct / total,
        "per_song": per_song_results,
    }


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("musicbrainz", "discogs", "all"):
        print("usage: run_llm_combo.py <musicbrainz|discogs|all>")
        sys.exit(1)

    scenario_argument = sys.argv[1]
    run_started_at = time.perf_counter()
    print(f"=== [{scenario_argument}] running {len(LLM_CANDIDATES)} LLM candidates concurrently ===")

    # Each candidate is a different provider with its own independent rate limit,
    # so there's no contention running them in parallel, unlike the source spikes
    # (MusicBrainz/Discogs/Wikidata) which each self-pace against one shared quota.
    with ThreadPoolExecutor(max_workers=len(LLM_CANDIDATES)) as executor:
        futures = [
            executor.submit(run_candidate, scenario_argument, provider, model)
            for provider, model in LLM_CANDIDATES
        ]
        candidate_results = [future.result() for future in futures]

    for candidate_result in candidate_results:
        print(
            f"  {candidate_result['provider']}/{candidate_result['model']}: "
            f"{candidate_result['correct']}/{candidate_result['total']} correct "
            f"({candidate_result['accuracy']:.0%}), {candidate_result['wrong']} wrong, "
            f"{candidate_result['no_answer']} no-answer"
        )

    output_path = RESULTS_DIR / f".llm_combo_results_{scenario_argument}.json"
    output_path.write_text(json.dumps(candidate_results, indent=2, ensure_ascii=False), encoding="utf-8")

    total_seconds = time.perf_counter() - run_started_at
    print(f"=== Scenario {scenario_argument!r} summary ===")
    for candidate_result in candidate_results:
        print(
            f"  {candidate_result['provider']}/{candidate_result['model']}: "
            f"{candidate_result['correct']} correct, {candidate_result['wrong']} wrong, "
            f"{candidate_result['no_answer']} no-answer, out of {candidate_result['total']} "
            f"({candidate_result['accuracy']:.0%})"
        )
    print(f"Total wall-clock time: {total_seconds:.1f}s")
    print(f"Saved to {output_path}")
