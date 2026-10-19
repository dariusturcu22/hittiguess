"""Final rollup: reads the three scenario result files run_llm_combo.py
produces (musicbrainz, discogs, all) and prints a single table comparing
the four remaining LLM candidates against each other across all three
combination scenarios. No new data gathering, all three scenario files
must already exist. See spikes/README.md.

Usage: python spikes/print_four_way_comparison.py
"""

import json
import sys
from pathlib import Path

RESULTS_DIR = Path(__file__).resolve().parent
SCENARIOS = ["musicbrainz", "discogs", "all"]
SCENARIO_LABELS = {"musicbrainz": "LLM+MusicBrainz", "discogs": "LLM+Discogs", "all": "LLM+all three"}

if __name__ == "__main__":
    scenario_results = {}
    for scenario in SCENARIOS:
        result_path = RESULTS_DIR / f".llm_combo_results_{scenario}.json"
        if not result_path.exists():
            print(f"Missing {result_path}, run run_llm_combo.py {scenario} first")
            sys.exit(1)
        scenario_results[scenario] = json.loads(result_path.read_text(encoding="utf-8"))

    candidate_labels = [f"{entry['provider']}/{entry['model']}" for entry in scenario_results["musicbrainz"]]

    print("=== Four-way LLM comparison across combination scenarios ===\n")
    header = f"{'LLM':<45}" + "".join(f"{SCENARIO_LABELS[scenario]:>20}" for scenario in SCENARIOS)
    print(header)
    for candidate_index, candidate_label in enumerate(candidate_labels):
        row = f"{candidate_label:<45}"
        for scenario in SCENARIOS:
            entry = scenario_results[scenario][candidate_index]
            row += f"{entry['correct']}/{entry['total']} ({entry['accuracy']:.0%})".rjust(20)
        print(row)
