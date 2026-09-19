"""One-off test: how accurate is each live-confirmed LLM candidate when
guessing a release year from title/artist alone, no source data, the same
smoke-test prompt already used to confirm structured output works.
Scores against the 21-song adversarial matrix's ground truth (18 songs
scoreable, 2 excluded as too recent for any training cutoff to know).
Not wired into anything else, a one-off accuracy check. See spikes/README.md.

Usage: python spikes/memory_accuracy_test.py
"""

import sys

sys.path.insert(0, ".")

from openai_compatible_spike import build_smoke_test_prompt, extract

GROUND_TRUTH: dict[str, tuple[int, ...]] = {
    "Never Gonna Give You Up": (1987,),
    "Bohemian Rhapsody": (1975,),
    "リサフランク420 / 現代のコンピュー": (2011,),
    "Dragostea Din Tei": (2003, 2004),
    "Blue Monday": (1983,),
    "Take On Me": (1984, 1985),
    "I Melt With You": (1982,),
    "Tainted Love": (1981,),
    "I Will Survive": (1978,),
    "Plastic Love": (1984, 1985),
    "Hurt": (2002, 2003),
    "I Fought the Law": (1979,),
    "Alpha and Omega": (2001,),
    "Palm Mall": (2014,),
    "Style": (2014, 2015),
    "Yesterday": (1965,),
    "Closer": (1994,),
    "Under Pressure": (1981,),
    "Say So": (2019, 2020),
}

ARTISTS: dict[str, str] = {
    "Never Gonna Give You Up": "Rick Astley",
    "Bohemian Rhapsody": "Queen",
    "リサフランク420 / 現代のコンピュー": "Macintosh Plus",
    "Dragostea Din Tei": "O-Zone",
    "Blue Monday": "New Order",
    "Take On Me": "a-ha",
    "I Melt With You": "Modern English",
    "Tainted Love": "Soft Cell",
    "I Will Survive": "Gloria Gaynor",
    "Plastic Love": "Mariya Takeuchi",
    "Hurt": "Johnny Cash",
    "I Fought the Law": "The Clash",
    "Alpha and Omega": "Boards of Canada",
    "Palm Mall": "猫 シ Corp",
    "Style": "Taylor Swift",
    "Yesterday": "The Beatles",
    "Closer": "Nine Inch Nails",
    "Under Pressure": "Queen & David Bowie",
    "Say So": "Doja Cat",
}

CANDIDATES = [
    ("openai", "gpt-5-nano"),
    ("openai", "gpt-5-mini"),
    ("groq", "openai/gpt-oss-20b"),
    ("deepinfra", "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo"),
    ("llamacpp", "Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf"),
]


def run_candidate(provider: str, model: str) -> tuple[int, int, int]:
    correct = 0
    wrong = 0
    no_answer = 0
    for title, acceptable_years in GROUND_TRUTH.items():
        artist = ARTISTS[title]
        prompt = build_smoke_test_prompt(title, artist)
        try:
            result = extract(provider, model, prompt)
        except Exception as error:
            print(f"    {title!r}: ERROR ({error})")
            no_answer += 1
            continue
        if result.release_year is None:
            outcome = "no answer"
            no_answer += 1
        elif result.release_year in acceptable_years:
            outcome = "correct"
            correct += 1
        else:
            outcome = f"WRONG (said {result.release_year}, truth {acceptable_years})"
            wrong += 1
        print(f"    {title!r}: {outcome}")
    return correct, wrong, no_answer


if __name__ == "__main__":
    total_songs = len(GROUND_TRUTH)
    summary = []
    for provider, model in CANDIDATES:
        print(f"=== {provider}/{model} ===")
        correct, wrong, no_answer = run_candidate(provider, model)
        summary.append((f"{provider}/{model}", correct, wrong, no_answer))
        print(f"  {correct}/{total_songs} correct ({correct / total_songs:.0%})\n")

    print("=== Summary ===")
    for label, correct, wrong, no_answer in summary:
        print(f"  {label}: {correct} correct, {wrong} wrong, {no_answer} no-answer, out of {total_songs}")
