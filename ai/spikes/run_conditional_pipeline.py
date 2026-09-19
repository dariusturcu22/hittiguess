"""The decided pipeline shape: MusicBrainz, Discogs, and Wikidata are
free, deterministic, and already fetched per song. If all three agree on
the same year, that's locked as the answer, zero LLM calls, Wikipedia
never gets queried or extracted. Only when they don't all agree does
Wikipedia get fetched and extracted (DeepSeek-V4-Flash, the decided
extraction model), and only then does the four-source reconciliation
(gpt-5-nano, the decided reconciliation model) run. Scores all 70 songs
(all_songs.py) and reports the split between locked and LLM-routed
songs alongside overall accuracy. See spikes/README.md.

Usage: python spikes/run_conditional_pipeline.py
"""

import time

import response_cache
import wikipedia_spike
from all_songs import ALL_SONGS, GROUND_TRUTH
from combo_prompts import build_four_sources_prompt, build_wikipedia_extraction_prompt
from openai_compatible_spike import extract

WIKIPEDIA_EXTRACTION_PROVIDER = "deepinfra"
WIKIPEDIA_EXTRACTION_MODEL = "deepseek-ai/DeepSeek-V4-Flash"
RECONCILIATION_PROVIDER = "openai"
RECONCILIATION_MODEL = "gpt-5-nano"


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


def get_wikipedia_entries(title: str, artist: str, album: str | None) -> list[dict]:
    """Fetches and caches Wikipedia's track/album extracts on demand, only
    called for songs the lock didn't resolve, this is the whole point of
    keeping Wikipedia conditional rather than always-on."""
    cached = response_cache.load("wikipedia", title, artist)
    if cached is not None:
        return cached
    track_page, track_extract = wikipedia_spike.find_and_extract(title, artist, "track")
    entries = []
    if track_extract:
        entries.append({"query": "track", "page_title": track_page, "extract": track_extract})
    if album:
        album_page, album_extract = wikipedia_spike.find_and_extract(album, artist, "album")
        if album_extract:
            entries.append({"query": "album", "page_title": album_page, "extract": album_extract})
    response_cache.save("wikipedia", title, artist, entries)
    return entries


def run_one(title: str, artist: str, album: str | None) -> dict:
    musicbrainz_candidates = response_cache.load("musicbrainz", title, artist) or []
    discogs_candidates = response_cache.load("discogs", title, artist) or []
    wikidata_candidates = response_cache.load("wikidata", title, artist) or []

    musicbrainz_year = source_earliest_year(musicbrainz_candidates)
    discogs_year = source_earliest_year(discogs_candidates)
    wikidata_year = source_earliest_year(wikidata_candidates)

    if musicbrainz_year is not None and musicbrainz_year == discogs_year == wikidata_year:
        return {"release_year": musicbrainz_year, "route": "locked", "llm_calls": 0}

    wikipedia_entries = get_wikipedia_entries(title, artist, album)
    extraction_prompt = build_wikipedia_extraction_prompt(title, artist, wikipedia_entries)
    extraction_result = extract(WIKIPEDIA_EXTRACTION_PROVIDER, WIKIPEDIA_EXTRACTION_MODEL, extraction_prompt)

    reconciliation_prompt = build_four_sources_prompt(
        title, artist, musicbrainz_candidates, discogs_candidates, wikidata_candidates,
        extraction_result.release_year, extraction_result.confidence,
    )
    reconciliation_result = extract(RECONCILIATION_PROVIDER, RECONCILIATION_MODEL, reconciliation_prompt)
    return {"release_year": reconciliation_result.release_year, "route": "llm", "llm_calls": 2}


if __name__ == "__main__":
    run_started_at = time.perf_counter()
    correct = wrong = no_answer = 0
    locked_count = llm_count = 0
    total_llm_calls = 0

    for song_index, (title, artist, album, tier, note) in enumerate(ALL_SONGS, start=1):
        acceptable_years = GROUND_TRUTH[title]
        outcome_data = run_one(title, artist, album)
        release_year = outcome_data["release_year"]
        total_llm_calls += outcome_data["llm_calls"]
        if outcome_data["route"] == "locked":
            locked_count += 1
        else:
            llm_count += 1

        if release_year is None:
            outcome = "no_answer"
            no_answer += 1
        elif release_year in acceptable_years:
            outcome = "correct"
            correct += 1
        else:
            outcome = "wrong"
            wrong += 1
        print(
            f"[{song_index}/{len(ALL_SONGS)}] [{tier}] {title!r}: {release_year} "
            f"[{outcome}] ({outcome_data['route']})"
        )

    total = len(ALL_SONGS)
    total_seconds = time.perf_counter() - run_started_at
    print("\n=== Conditional pipeline results (all 70 songs) ===")
    print(f"{correct}/{total} correct ({correct / total:.0%}), {wrong} wrong, {no_answer} no answer")
    print(f"Locked (no LLM at all): {locked_count}/{total} ({locked_count / total:.0%})")
    print(f"Routed to Wikipedia + LLM reconciliation: {llm_count}/{total} ({llm_count / total:.0%})")
    print(f"Total LLM calls made: {total_llm_calls} (vs. {total * 2} if every song always used both LLM steps)")
    print(f"Total wall-clock time: {total_seconds:.1f}s")
