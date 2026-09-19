"""Gitignored on-disk cache of structured candidate data extracted from
each source's live API responses, keyed by title/artist. Lets the
LLM-combination tests (run_llm_combo.py) reuse a source's already-fetched
data instead of re-querying it, so the same 49-song run underpins both the
standalone source-accuracy comparison and every LLM-combination scenario.

One file per source (not one shared file) since MusicBrainz, Discogs, and
Wikidata are each written by their own separate process running
concurrently; a single shared file would need cross-process locking this
doesn't attempt. See spikes/README.md.
"""

import json
from pathlib import Path

_CACHE_DIR = Path(__file__).resolve().parent


def _cache_file_path(source: str) -> Path:
    return _CACHE_DIR / f".source_response_cache_{source}.json"


def _cache_key(title: str, artist: str) -> str:
    return f"{title}|||{artist}"


def save(source: str, title: str, artist: str, candidates: list[dict]) -> None:
    """candidates is a list of whatever this source's meaningful
    year-bearing data points are (title/artist/date/type/score for
    MusicBrainz and Discogs, publication-date/description for Wikidata),
    the same shape the combination prompts read from, not the full nested
    raw API response, most of which is irrelevant to year reconciliation.
    Rewrites the whole file on every call, simpler than an append-only
    format and cheap at this data size (well under a thousand entries)."""
    cache_file_path = _cache_file_path(source)
    cache = json.loads(cache_file_path.read_text(encoding="utf-8")) if cache_file_path.exists() else {}
    cache[_cache_key(title, artist)] = candidates
    cache_file_path.write_text(json.dumps(cache, indent=2, ensure_ascii=False), encoding="utf-8")


def load(source: str, title: str, artist: str) -> list[dict] | None:
    cache_file_path = _cache_file_path(source)
    if not cache_file_path.exists():
        return None
    cache = json.loads(cache_file_path.read_text(encoding="utf-8"))
    return cache.get(_cache_key(title, artist))


def load_source(source: str) -> dict[str, list[dict]]:
    cache_file_path = _cache_file_path(source)
    if not cache_file_path.exists():
        return {}
    return json.loads(cache_file_path.read_text(encoding="utf-8"))
