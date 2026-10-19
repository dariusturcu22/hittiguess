"""Tests a second round of cheap-tier LLM candidates (found while pricing
gpt-5-nano against DeepInfra's catalog) against the "all three sources"
combination scenario only, the strongest-performing scenario from the
first four-candidate round, using the already-cached MusicBrainz/Discogs/
Wikidata data. Parallelized across candidates, same as run_llm_combo.py's
"all" scenario. See spikes/README.md.

Usage: python spikes/test_new_candidates.py
"""

import json
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from run_llm_combo import run_candidate

RESULTS_DIR = Path(__file__).resolve().parent

NEW_CANDIDATES = [
    ("openai", "gpt-5.6-luna"),
    ("deepinfra", "deepseek-ai/DeepSeek-V4-Flash"),
    ("deepinfra", "google/gemma-4-26B-A4B-it"),
    # nvidia/NVIDIA-Nemotron-3-Super-120B-A12B dropped: hung indefinitely
    # twice in a row on DeepInfra, not a candidate worth pursuing regardless
    # of accuracy potential. Its one completed partial run (17/18 correct
    # before stalling) is reported in conversation but not saved to disk.
]

if __name__ == "__main__":
    run_started_at = time.perf_counter()
    print(f"=== [all] running {len(NEW_CANDIDATES)} new candidates concurrently ===")

    with ThreadPoolExecutor(max_workers=len(NEW_CANDIDATES)) as executor:
        futures = [executor.submit(run_candidate, "all", provider, model) for provider, model in NEW_CANDIDATES]
        candidate_results = [future.result() for future in futures]

    for candidate_result in candidate_results:
        print(
            f"  {candidate_result['provider']}/{candidate_result['model']}: "
            f"{candidate_result['correct']}/{candidate_result['total']} correct "
            f"({candidate_result['accuracy']:.0%}), {candidate_result['wrong']} wrong, "
            f"{candidate_result['no_answer']} no-answer"
        )

    output_path = RESULTS_DIR / ".llm_combo_results_all_new_candidates.json"
    output_path.write_text(json.dumps(candidate_results, indent=2, ensure_ascii=False), encoding="utf-8")

    total_seconds = time.perf_counter() - run_started_at
    print(f"\nTotal wall-clock time: {total_seconds:.1f}s")
    print(f"Saved to {output_path}")
    print("\nFor comparison, gpt-5-nano scored 45/49 (92%) on this same scenario.")
