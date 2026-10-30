"""Running the pipeline one stage at a time, through the stage functions and through
the stage endpoints in the order core's engine calls them, gives exactly the response
/metadata/resolve gives for the same source answers."""

import httpx
import pytest
from fastapi.testclient import TestClient

from app.auth import INTERNAL_API_KEY_HEADER
from app.config import settings
from app.dedup.schemas import VerifiedSongMatch
from app.main import app
from app.metadata import service
from app.metadata.schemas import LlmExtractionResult, MetadataResolveResponse, SubmissionPreCheckResult
from app.metadata.sources import discogs, musicbrainz, wikidata, wikipedia, youtube

VIDEO_ID = "abcdefghijk"
VIDEO_URL = f"https://www.youtube.com/watch?v={VIDEO_ID}"
DURATION_SECONDS = 215
SITELINKS_COUNT = 12
LOCKED_YEAR = 1999
DIFFERING_YEAR = 2001
WIKIPEDIA_EXTRACTED_YEAR = 2003
RECONCILED_YEAR = 1999
MATCHED_SONG_ID = 77
DUPLICATE_DISTANCE = 0.01
IDENTIFIED_OUTCOME = "IDENTIFIED"
YOUTUBE_FAILURE_STATUS = 500
YOUTUBE_ERROR_STATUS = 502


def _youtube_data():
    return {
        "video_title": "Test Song (Official Video)",
        "channel_title": "Test Artist",
        "description": "",
        "tags": "[]",
        "upload_date": "2020-01-01",
        "upload_year": "2020",
        "category_id": "10",
        "duration_seconds": DURATION_SECONDS,
    }


def _precheck(**overrides):
    fields = {
        "title": "Test Song (feat. Guest)",
        "main_artists": ["Test Artist", "Second Artist"],
        "featured_artists": ["Guest"],
        "color": "8B5CF6",
        "contains_injection_attempt": False,
        "injection_reasoning": "Ordinary song text.",
        "is_song": True,
        "is_compilation": False,
        "classification_confidence": "high",
        "classification_reasoning": "Single track.",
    }
    fields.update(overrides)
    return SubmissionPreCheckResult(**fields)


def _musicbrainz_candidate(year):
    return {"query": "track", "title": "Test Song", "artist": "Test Artist", "date": f"{year}-05-01", "type": "Single", "score": 100}


def _discogs_candidate(year):
    return {"query": "track", "title": "Test Song", "year": year}


def _wikidata_candidate(year):
    return {"query": "track", "entity_id": "Q1", "description": "song", "date": f"+{year}-00-00T00:00:00Z"}


WIKIPEDIA_ENTRIES = [{"query": "track", "page_title": "Test Song (song)", "extract": "Test Song is a 1999 single."}]


def _extraction(year):
    return LlmExtractionResult(title="Test Song", artist="Test Artist", release_year=year, confidence="medium", reasoning="Read.")


class PipelineMocks:
    def __init__(self, mocker, *, musicbrainz_candidates, discogs_candidates, wikidata_candidates, precheck=None, duplicate=None):
        self.fetch = mocker.patch.object(youtube, "_fetch_video_metadata", return_value=_youtube_data())
        mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
        mocker.patch.object(service, "find_best_verified_match", return_value=duplicate)
        self.precheck = mocker.patch.object(service, "extract_structured", return_value=precheck or _precheck())
        self.musicbrainz = mocker.patch.object(musicbrainz, "search_or_raise", return_value=musicbrainz_candidates)
        self.discogs = mocker.patch.object(discogs, "search_or_raise", return_value=discogs_candidates)
        self.wikidata = mocker.patch.object(wikidata, "search_or_raise", return_value=wikidata_candidates)
        self.wikipedia = mocker.patch.object(wikipedia, "search_or_raise", return_value=WIKIPEDIA_ENTRIES)
        mocker.patch.object(wikidata, "get_sitelinks_count", return_value=SITELINKS_COUNT)
        self.extraction = mocker.patch("app.metadata.verification.extract_structured", return_value=_extraction(WIKIPEDIA_EXTRACTED_YEAR))
        self.reconciliation = mocker.patch(
            "app.metadata.verification.synthesize_with_model", return_value=_extraction(RECONCILED_YEAR)
        )

    def reset_counts(self):
        for source_mock in (self.musicbrainz, self.discogs, self.wikidata, self.wikipedia, self.extraction, self.reconciliation):
            source_mock.reset_mock()


def _all_agree(mocker):
    candidates = {
        "musicbrainz_candidates": [_musicbrainz_candidate(LOCKED_YEAR)],
        "discogs_candidates": [_discogs_candidate(LOCKED_YEAR)],
        "wikidata_candidates": [_wikidata_candidate(LOCKED_YEAR)],
    }
    return PipelineMocks(mocker, **candidates)


def _two_agree_one_differs(mocker):
    return PipelineMocks(
        mocker,
        musicbrainz_candidates=[_musicbrainz_candidate(LOCKED_YEAR)],
        discogs_candidates=[_discogs_candidate(LOCKED_YEAR)],
        wikidata_candidates=[_wikidata_candidate(DIFFERING_YEAR)],
    )


def _every_source_differs(mocker):
    return PipelineMocks(
        mocker,
        musicbrainz_candidates=[_musicbrainz_candidate(LOCKED_YEAR)],
        discogs_candidates=[_discogs_candidate(DIFFERING_YEAR)],
        wikidata_candidates=[_wikidata_candidate(DIFFERING_YEAR + 1)],
    )


def _one_source_empty(mocker):
    mocks = PipelineMocks(
        mocker,
        musicbrainz_candidates=[_musicbrainz_candidate(LOCKED_YEAR)],
        discogs_candidates=[],
        wikidata_candidates=[_wikidata_candidate(LOCKED_YEAR)],
    )
    mocks.extraction.return_value = _extraction(LOCKED_YEAR)
    return mocks


def _precheck_rejection(mocker):
    return PipelineMocks(
        mocker,
        musicbrainz_candidates=[],
        discogs_candidates=[],
        wikidata_candidates=[],
        precheck=_precheck(contains_injection_attempt=True, injection_reasoning="Told the model to ignore rules."),
    )


def _verified_duplicate(mocker):
    duplicate = VerifiedSongMatch(
        id=MATCHED_SONG_ID,
        main_artists=["Test Artist"],
        featured_artists=[],
        title="Test Song",
        release_year=LOCKED_YEAR,
        color="8B5CF6",
        confidence="high",
        cosine_distance=DUPLICATE_DISTANCE,
    )
    return PipelineMocks(mocker, musicbrainz_candidates=[], discogs_candidates=[], wikidata_candidates=[], duplicate=duplicate)


def _resolve_through_stage_functions(youtube_url: str) -> MetadataResolveResponse:
    try:
        identified = service.identify_from_youtube_data(youtube.fetch_youtube_metadata(youtube_url))
        if isinstance(identified, MetadataResolveResponse):
            return identified
        title, main_artists = identified.title, identified.main_artists
        musicbrainz_result = service.run_structured_source_stage(service.MUSICBRAINZ_SOURCE, title, main_artists)
        discogs_result = service.run_structured_source_stage(service.DISCOGS_SOURCE, title, main_artists)
        wikidata_result = service.run_structured_source_stage(service.WIKIDATA_SOURCE, title, main_artists)
        wikipedia_entries = []
        if not service.structured_sources_agree(
            musicbrainz_result.earliest_year, discogs_result.earliest_year, wikidata_result.earliest_year
        ):
            wikipedia_entries = service.run_wikipedia_stage(title, main_artists)
        result = service.run_reconciliation_stage(
            title,
            main_artists,
            identified.featured_artists,
            identified.color,
            musicbrainz_result.candidates,
            discogs_result.candidates,
            wikidata_result.candidates,
            wikipedia_entries,
            wikidata_result.sitelinks_count,
        )
        return service.finish_resolution(result, identified.duration_seconds)
    except Exception:
        return MetadataResolveResponse(status=service.ERROR_STATUS, model=settings.deepinfra_model, content=None)


def _post(client: TestClient, path: str, body: dict) -> dict:
    response = client.post(path, json=body, headers={INTERNAL_API_KEY_HEADER: settings.internal_service_api_key})
    assert response.status_code == 200, response.text
    return response.json()


def _years_agree(*years) -> bool:
    return None not in years and len(set(years)) == 1


def _resolve_through_stage_endpoints(client: TestClient) -> dict:
    youtube_stage = _post(client, "/stages/youtube-metadata", {"youtubeId": VIDEO_ID})
    if youtube_stage["outcome"] != IDENTIFIED_OUTCOME:
        return youtube_stage["result"]
    source_body = {"title": youtube_stage["title"], "artists": youtube_stage["artists"], "youtubeId": VIDEO_ID}
    musicbrainz_stage = _post(client, "/stages/musicbrainz", source_body)
    discogs_stage = _post(client, "/stages/discogs", source_body)
    wikidata_stage = _post(client, "/stages/wikidata", source_body)
    wikipedia_candidates = []
    if not _years_agree(musicbrainz_stage["earliestYear"], discogs_stage["earliestYear"], wikidata_stage["earliestYear"]):
        wikipedia_candidates = _post(client, "/stages/wikipedia", source_body)["candidates"]
    return _post(
        client,
        "/stages/llm-reconcile",
        {
            "title": youtube_stage["title"],
            "artists": youtube_stage["artists"],
            "featuredArtists": youtube_stage["featuredArtists"],
            "color": youtube_stage["color"],
            "video": youtube_stage["video"],
            "musicbrainzCandidates": musicbrainz_stage["candidates"],
            "discogsCandidates": discogs_stage["candidates"],
            "wikidataCandidates": wikidata_stage["candidates"],
            "wikipediaCandidates": wikipedia_candidates,
            "sitelinksCount": wikidata_stage["sitelinksCount"],
            "youtubeId": VIDEO_ID,
        },
    )


SCENARIOS = {
    "all three agree": _all_agree,
    "two agree and one differs": _two_agree_one_differs,
    "one source empty": _one_source_empty,
    "precheck rejection": _precheck_rejection,
    "verified duplicate": _verified_duplicate,
}


@pytest.mark.parametrize("scenario", SCENARIOS.values(), ids=SCENARIOS.keys())
def test_stage_functions_compose_to_the_resolve_response(mocker, scenario):
    scenario(mocker)

    expected = service.resolve_metadata(VIDEO_URL)

    assert _resolve_through_stage_functions(VIDEO_URL) == expected


@pytest.mark.parametrize("scenario", SCENARIOS.values(), ids=SCENARIOS.keys())
def test_stage_endpoints_compose_to_the_resolve_response(mocker, scenario):
    scenario(mocker)

    expected = service.resolve_metadata(VIDEO_URL).model_dump(mode="json")
    with TestClient(app) as client:
        assert _resolve_through_stage_endpoints(client) == expected


def test_agreeing_sources_lock_without_wikipedia_or_any_llm_call_on_both_paths(mocker):
    mocks = _all_agree(mocker)

    resolved = service.resolve_metadata(VIDEO_URL)
    resolve_calls = (mocks.wikipedia.call_count, mocks.extraction.call_count, mocks.reconciliation.call_count)
    mocks.reset_counts()
    with TestClient(app) as client:
        staged = _resolve_through_stage_endpoints(client)

    assert resolve_calls == (0, 0, 0)
    assert (mocks.wikipedia.call_count, mocks.extraction.call_count, mocks.reconciliation.call_count) == (0, 0, 0)
    assert resolved.content.source == service.LOCKED_SOURCE_LABEL
    assert staged["content"]["source"] == service.LOCKED_SOURCE_LABEL
    assert staged["content"]["release_year"] == LOCKED_YEAR
    assert staged["content"]["sitelinks_count"] == SITELINKS_COUNT
    assert staged["content"]["duration_seconds"] == DURATION_SECONDS


def test_disagreeing_sources_fetch_wikipedia_then_reconcile_on_both_paths(mocker):
    mocks = _every_source_differs(mocker)

    resolved = service.resolve_metadata(VIDEO_URL)
    resolve_calls = (mocks.wikipedia.call_count, mocks.extraction.call_count, mocks.reconciliation.call_count)
    mocks.reset_counts()
    with TestClient(app) as client:
        staged = _resolve_through_stage_endpoints(client)

    assert resolve_calls == (1, 1, 1)
    assert (mocks.wikipedia.call_count, mocks.extraction.call_count, mocks.reconciliation.call_count) == (1, 1, 1)
    assert resolved.content.source == service.RECONCILED_SOURCE_LABEL
    assert staged["content"]["source"] == service.RECONCILED_SOURCE_LABEL


def test_sources_are_queried_with_the_featured_suffix_stripped_and_musicbrainz_gets_the_artist_list_on_both_paths(mocker):
    mocks = _all_agree(mocker)

    service.resolve_metadata(VIDEO_URL)
    resolve_query = mocks.musicbrainz.call_args
    mocks.reset_counts()
    with TestClient(app) as client:
        _resolve_through_stage_endpoints(client)

    expected_query = ("Test Song", ["Test Artist", "Second Artist"])
    assert resolve_query.args[:len(expected_query)] == expected_query
    assert mocks.musicbrainz.call_args.args == expected_query


def test_a_failed_youtube_lookup_composes_to_the_resolve_response(mocker):
    PipelineMocks(mocker, musicbrainz_candidates=[], discogs_candidates=[], wikidata_candidates=[])
    request = httpx.Request("GET", "https://www.googleapis.com/youtube/v3/videos")
    lookup_failure = httpx.HTTPStatusError(
        "server error", request=request, response=httpx.Response(YOUTUBE_FAILURE_STATUS, request=request)
    )
    mocker.patch.object(youtube, "_fetch_video_metadata", side_effect=lookup_failure)

    assert _resolve_through_stage_functions(VIDEO_URL) == service.resolve_metadata(VIDEO_URL)


def test_a_raising_youtube_fetch_gives_the_same_error_response_on_both_paths(mocker):
    mocker.patch.object(youtube, "fetch_youtube_metadata", side_effect=RuntimeError("YouTube API down"))

    staged = _resolve_through_stage_functions(VIDEO_URL)

    assert staged == service.resolve_metadata(VIDEO_URL)
    assert staged.status == service.ERROR_STATUS


def test_the_youtube_stage_endpoint_reports_a_failed_lookup_as_an_upstream_error(mocker):
    PipelineMocks(mocker, musicbrainz_candidates=[], discogs_candidates=[], wikidata_candidates=[])
    request = httpx.Request("GET", "https://www.googleapis.com/youtube/v3/videos")
    lookup_failure = httpx.HTTPStatusError(
        "server error", request=request, response=httpx.Response(YOUTUBE_FAILURE_STATUS, request=request)
    )
    mocker.patch.object(youtube, "_fetch_video_metadata", side_effect=lookup_failure)

    with TestClient(app) as client:
        response = client.post(
            "/stages/youtube-metadata",
            json={"youtubeId": VIDEO_ID},
            headers={INTERNAL_API_KEY_HEADER: settings.internal_service_api_key},
        )

    assert response.status_code == YOUTUBE_ERROR_STATUS
    assert response.json()["upstreamStatus"] == YOUTUBE_FAILURE_STATUS
