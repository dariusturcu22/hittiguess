"""The fast tier from DECISIONS.md's 2026-09 two-tier entry: one source per song
instead of the patient tier's three plus reconciliation. Each song goes to whichever
lane, MusicBrainz or Wikipedia plus LLM extraction, frees up soonest given the work
already in flight on it, the live form of the spike's work-stealing dispatch in
spikes/run_fast_tier_dispatch.py. A lane that finds nothing hands the song to the
other lane. Every answer is provisional: the backend queues it for the patient tier."""

import logging
import threading
from contextlib import contextmanager

from app.config import settings
from app.metadata.schemas import FastDateResponse, IdentifiedSong, IdentifyResponse
from app.metadata.service import (
    ERROR_STATUS,
    SUCCESS_STATUS,
    MetadataResolveResponse,
    identify_submission,
)
from app.metadata.sources import musicbrainz, wikipedia
from app.metadata.sources.util import strip_featured_artist_suffix
from app.metadata.verification import _extract_earliest_year, _run_wikipedia_extraction

logger = logging.getLogger(__name__)

MUSICBRAINZ_LANE = "musicbrainz"
WIKIPEDIA_LANE = "wikipedia"
# Observed per-song cost in the fast-tier spike: MusicBrainz's paced API calls versus
# Wikipedia's fetch plus its extraction call.
EXPECTED_SECONDS_PER_SONG = {MUSICBRAINZ_LANE: 3.0, WIKIPEDIA_LANE: 4.5}
PROVISIONAL_CONFIDENCE = "low"
SOURCE_LABEL_PREFIX = "fast-tier-"
NO_ANSWER_SOURCE_LABEL = "fast-tier-no-answer"


class LaneDispatcher:
    """Tracks the songs in flight on each lane and hands the next one to the lane
    expected to finish its current queue first."""

    def __init__(self, expected_seconds_per_song: dict[str, float]) -> None:
        self._expected_seconds_per_song = expected_seconds_per_song
        self._in_flight = {lane: 0 for lane in expected_seconds_per_song}
        self._lock = threading.Lock()

    def lane_order(self) -> list[str]:
        with self._lock:
            return sorted(
                self._in_flight,
                key=lambda lane: (self._in_flight[lane] + 1) * self._expected_seconds_per_song[lane],
            )

    @contextmanager
    def occupy(self, lane: str):
        with self._lock:
            self._in_flight[lane] += 1
        try:
            yield
        finally:
            with self._lock:
                self._in_flight[lane] -= 1


lane_dispatcher = LaneDispatcher(EXPECTED_SECONDS_PER_SONG)


def _musicbrainz_year(title: str, artist: str) -> int | None:
    return _extract_earliest_year(musicbrainz.search(title, artist))


def _wikipedia_year(title: str, artist: str) -> int | None:
    entries = wikipedia.search(title, artist)
    if not entries:
        return None
    extraction = _run_wikipedia_extraction(title, artist, entries)
    return extraction.release_year if extraction else None


LANE_LOOKUPS = {MUSICBRAINZ_LANE: _musicbrainz_year, WIKIPEDIA_LANE: _wikipedia_year}


def identify(youtube_url: str) -> IdentifyResponse:
    try:
        outcome = identify_submission(youtube_url)
    except Exception as identify_error:
        logger.warning("Fast-tier identify failed: %s", identify_error)
        return IdentifyResponse(status=ERROR_STATUS, model=settings.deepinfra_model)

    if isinstance(outcome, MetadataResolveResponse):
        return IdentifyResponse(
            status=outcome.status,
            model=outcome.model,
            duplicate=outcome.content,
            rejection_reason=outcome.rejection_reason,
            rejection_detail=outcome.rejection_detail,
        )
    return IdentifyResponse(
        status=SUCCESS_STATUS,
        model=settings.deepinfra_model,
        identified=IdentifiedSong(
            title=outcome.title,
            main_artists=outcome.main_artists,
            featured_artists=outcome.featured_artists,
            color=outcome.color,
        ),
    )


def date_fast(title: str, main_artists: list[str]) -> FastDateResponse:
    display_artists = " & ".join(main_artists)
    query_title = strip_featured_artist_suffix(title)
    for lane in lane_dispatcher.lane_order():
        with lane_dispatcher.occupy(lane):
            try:
                release_year = LANE_LOOKUPS[lane](query_title, display_artists)
            except Exception as lane_error:
                logger.warning("Fast-tier %s lane failed for %r: %s", lane, title, lane_error)
                release_year = None
        if release_year is not None:
            return FastDateResponse(
                release_year=release_year,
                confidence=PROVISIONAL_CONFIDENCE,
                source=f"{SOURCE_LABEL_PREFIX}{lane}",
                lane=lane,
            )
    return FastDateResponse(release_year=None, confidence=PROVISIONAL_CONFIDENCE, source=NO_ANSWER_SOURCE_LABEL, lane=None)
