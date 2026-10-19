"""Simulates the fast tier's dynamic work-stealing dispatch: two lanes,
MusicBrainz-only and Wikipedia-only (fetch + DeepSeek extraction), pulling
from one shared queue of all 70 songs, each lane grabbing the next song
the moment it's free rather than a fixed pre-assigned split. Reuses every
answer already computed and cached (no new API or LLM calls needed for
correctness), but each lane sleeps for its own real observed per-song
duration before "returning" its cached answer, so the dispatch
pattern, how many songs each lane actually ends up handling, reflects
genuine relative speed: MusicBrainz's plain API calls versus Wikipedia's
fetch-plus-LLM-extraction round trip, not an arbitrary 50/50 assumption.
See spikes/README.md.

Usage: python spikes/run_fast_tier_dispatch.py
"""

import json
import queue
import threading
import time

import response_cache
from all_songs import ALL_SONGS, GROUND_TRUTH

# MusicBrainz's own paced delay is ~1.49s/call (67% of the documented
# 60/min ceiling), averaging close to 2 calls/song (track + album).
# Wikipedia's fetch is fast (~0.3-0.6s/call), but its LLM extraction call
# is the real cost, averaging several seconds per call once queuing and
# generation are counted.
MUSICBRAINZ_SECONDS_PER_SONG = 3.0
WIKIPEDIA_SECONDS_PER_SONG = 4.5


def source_earliest_year(candidates: list[dict]) -> int | None:
    resolved = []
    for candidate in candidates:
        if "year" in candidate and candidate["year"] is not None:
            resolved.append(candidate["year"])
        elif "date" in candidate and candidate["date"]:
            digits = candidate["date"].lstrip("+-")[:4]
            if digits.isdigit():
                resolved.append(int(digits))
    return min(resolved) if resolved else None


def _load_wikipedia_answers() -> dict[str, int | None]:
    mainstream = {row["title"]: row["release_year"] for row in json.load(open(".mainstream_wikipedia_extraction.json", encoding="utf-8"))}
    original = json.load(open(".llm_combo_results_wikipedia_extraction.json", encoding="utf-8"))
    deepseek = next(entry for entry in original if entry["model"] == "deepseek-ai/DeepSeek-V4-Flash")
    original_answers = {song["title"]: song.get("release_year") for song in deepseek["per_song"]}
    return {**original_answers, **mainstream}


def musicbrainz_answer(title: str, artist: str) -> int | None:
    return source_earliest_year(response_cache.load("musicbrainz", title, artist) or [])


if __name__ == "__main__":
    wikipedia_answers = _load_wikipedia_answers()
    work_queue: "queue.Queue" = queue.Queue()
    for song in ALL_SONGS:
        work_queue.put(song)

    results_lock = threading.Lock()
    per_song_results: list[dict] = []
    lane_song_counts = {"musicbrainz": 0, "wikipedia": 0}

    def _lane_worker(lane_name: str, seconds_per_song: float, answer_fn) -> None:
        while True:
            try:
                title, artist, album, tier, note = work_queue.get_nowait()
            except queue.Empty:
                return
            time.sleep(seconds_per_song)
            year = answer_fn(title, artist)
            acceptable_years = GROUND_TRUTH[title]
            outcome = "no_answer" if year is None else ("correct" if year in acceptable_years else "wrong")
            with results_lock:
                per_song_results.append({"title": title, "lane": lane_name, "release_year": year, "outcome": outcome})
                lane_song_counts[lane_name] += 1
            print(f"[{lane_name}] {title!r}: {year} [{outcome}]")
            work_queue.task_done()

    run_started_at = time.perf_counter()
    threads = [
        threading.Thread(target=_lane_worker, args=("musicbrainz", MUSICBRAINZ_SECONDS_PER_SONG, musicbrainz_answer)),
        threading.Thread(target=_lane_worker, args=("wikipedia", WIKIPEDIA_SECONDS_PER_SONG, lambda title, artist: wikipedia_answers.get(title))),
    ]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    total_seconds = time.perf_counter() - run_started_at

    total = len(per_song_results)
    correct = sum(1 for r in per_song_results if r["outcome"] == "correct")
    wrong = sum(1 for r in per_song_results if r["outcome"] == "wrong")
    no_answer = sum(1 for r in per_song_results if r["outcome"] == "no_answer")

    print("\n=== Fast-tier dynamic dispatch results (all 70 songs) ===")
    print(f"{correct}/{total} correct ({correct / total:.0%}), {wrong} wrong, {no_answer} no answer")
    print(f"MusicBrainz lane handled: {lane_song_counts['musicbrainz']}/{total} songs")
    print(f"Wikipedia lane handled: {lane_song_counts['wikipedia']}/{total} songs")
    print(f"Simulated wall-clock time: {total_seconds:.1f}s")

    for lane in ("musicbrainz", "wikipedia"):
        lane_results = [r for r in per_song_results if r["lane"] == lane]
        lane_correct = sum(1 for r in lane_results if r["outcome"] == "correct")
        print(f"  {lane}: {lane_correct}/{len(lane_results)} correct within its own lane")
