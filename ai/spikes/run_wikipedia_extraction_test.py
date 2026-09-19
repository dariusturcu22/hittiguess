"""Tests reading-comprehension extraction (not reconciliation among
structured candidates, a genuinely different skill, see
combo_prompts.build_wikipedia_extraction_prompt) against every LLM
candidate touched so far in this round: the original four plus the four
found while pricing gpt-5-nano against DeepInfra's catalog. Reads cached
Wikipedia lead extracts (response_cache.py, populated by
run_full_wikipedia.py), no live Wikipedia calls here. Parallelized across
candidates. See spikes/README.md.

Usage: python spikes/run_wikipedia_extraction_test.py
"""

import json
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import response_cache
from all_songs import ALL_SONGS, GROUND_TRUTH
from combo_prompts import build_wikipedia_extraction_prompt
from openai_compatible_spike import extract

RESULTS_DIR = Path(__file__).resolve().parent

CANDIDATES = [
    ("openai", "gpt-5-nano"),
    ("openai", "gpt-5-mini"),
    ("groq", "openai/gpt-oss-20b"),
    ("deepinfra", "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo"),
    ("openai", "gpt-5.6-luna"),
    ("deepinfra", "deepseek-ai/DeepSeek-V4-Flash"),
    ("deepinfra", "google/gemma-4-26B-A4B-it"),
    # nvidia/NVIDIA-Nemotron-3-Super-120B-A12B dropped: hung indefinitely twice
    # in a row on DeepInfra (zero progress even past a 90s request timeout),
    # not a candidate worth pursuing regardless of accuracy potential.
]


def run_candidate(provider: str, model: str) -> dict:
    per_song_results = []
    correct = wrong = no_answer = 0
    for title, artist, album, tier, note in ALL_SONGS:
        acceptable_years = GROUND_TRUTH[title]
        cached = response_cache.load("wikipedia", title, artist) or []
        prompt = build_wikipedia_extraction_prompt(title, artist, cached)
        try:
            result = extract(provider, model, prompt)
            release_year = result.release_year
        except Exception as error:
            per_song_results.append({"title": title, "artist": artist, "outcome": "error", "error": str(error)})
            no_answer += 1
            print(f"    [{provider}/{model}] {title!r}: ERROR ({error})")
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
    run_started_at = time.perf_counter()
    print(f"=== [wikipedia-extraction] running {len(CANDIDATES)} candidates concurrently ===")

    with ThreadPoolExecutor(max_workers=len(CANDIDATES)) as executor:
        futures = [executor.submit(run_candidate, provider, model) for provider, model in CANDIDATES]
        candidate_results = [future.result() for future in futures]

    output_path = RESULTS_DIR / ".llm_combo_results_wikipedia_extraction.json"
    output_path.write_text(json.dumps(candidate_results, indent=2, ensure_ascii=False), encoding="utf-8")

    total_seconds = time.perf_counter() - run_started_at
    print("\n=== Wikipedia extraction results ===")
    for candidate_result in candidate_results:
        print(
            f"  {candidate_result['provider']}/{candidate_result['model']}: "
            f"{candidate_result['correct']}/{candidate_result['total']} correct "
            f"({candidate_result['accuracy']:.0%}), {candidate_result['wrong']} wrong, "
            f"{candidate_result['no_answer']} no-answer"
        )
    print(f"Total wall-clock time: {total_seconds:.1f}s")
    print(f"Saved to {output_path}")
