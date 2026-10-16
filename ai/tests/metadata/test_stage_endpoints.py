import logging

import httpx
import openai
import pytest
import respx
from fastapi.testclient import TestClient

from app.auth import INTERNAL_API_KEY_HEADER
from app.config import settings
from app.dedup.schemas import VerifiedSongMatch
from app.main import app
from app.metadata import service
from app.metadata.schemas import SubmissionPreCheckResult
from app.metadata.sources import discogs, musicbrainz, pace_state, wikidata, wikipedia, youtube
from app.metadata.sources.pace_state import STAGE_MUSICBRAINZ, PaceWaitTooLong, ReservedSlot
from app.metadata.stages.router import MAXIMUM_STAGE_PACE_WAIT_SECONDS
from app.observability.logging_config import JsonLineFormatter

VIDEO_ID = "abcdefghijk"
YOUTUBE_VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos"
MUSICBRAINZ_URL = "https://musicbrainz.org/ws/2/release-group/"
DISCOGS_SEARCH_URL = "https://api.discogs.com/database/search"
INVALID_INTERNAL_API_KEY = "invalid-internal-key"
DURATION_SECONDS = 215
LONG_VIDEO_SECONDS = 3600
NON_MUSIC_CATEGORY = "22"
LOCKED_YEAR = 1999
DIFFERING_YEAR = 2001
WIKIPEDIA_YEAR = 2003
SITELINKS_COUNT = 9
MATCHED_SONG_ID = 41
DUPLICATE_DISTANCE = 0.01
OVER_CAP_WAIT_SECONDS = 42.3
OVER_CAP_RETRY_AFTER_SECONDS = 43
SUB_SECOND_WAIT_SECONDS = 0.2
MINIMUM_RETRY_AFTER_SECONDS = 1

HTTP_OK = 200
HTTP_UNAUTHORIZED = 401
HTTP_FORBIDDEN = 403
HTTP_UNPROCESSABLE = 422
HTTP_TOO_MANY_REQUESTS = 429
HTTP_INTERNAL_ERROR = 500
HTTP_BAD_GATEWAY = 502
HTTP_SERVICE_UNAVAILABLE = 503
HTTP_GATEWAY_TIMEOUT = 504

SOURCE_STAGE_PATHS = ("/stages/musicbrainz", "/stages/discogs", "/stages/wikidata", "/stages/wikipedia")
ALL_STAGE_PATHS = ("/stages/youtube-metadata", *SOURCE_STAGE_PATHS, "/stages/llm-reconcile")
SOURCE_BODY = {"title": "Test Song (feat. Guest)", "artists": ["Test Artist"], "youtubeId": VIDEO_ID}


def _headers():
    return {INTERNAL_API_KEY_HEADER: settings.internal_service_api_key}


@pytest.fixture
def client():
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture(autouse=True)
def no_sleeping(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    mocker.patch("app.metadata.sources.http_retry.time.sleep")


def _youtube_data(**overrides):
    data = {
        "video_title": "Test Song (Official Video)",
        "channel_title": "Test Artist",
        "description": "",
        "category_id": "10",
        "duration_seconds": DURATION_SECONDS,
    }
    data.update(overrides)
    return data


def _precheck(**overrides):
    fields = {
        "title": "Test Song",
        "main_artists": ["Test Artist"],
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


def _patch_identification(mocker, youtube_data=None, precheck=None, duplicate=None):
    mocker.patch.object(youtube, "_fetch_video_metadata", return_value=youtube_data or _youtube_data())
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=duplicate)
    return mocker.patch.object(service, "extract_structured", return_value=precheck or _precheck())


def _status_error(status_code, url=YOUTUBE_VIDEOS_URL):
    request = httpx.Request("GET", url)
    return httpx.HTTPStatusError("error status", request=request, response=httpx.Response(status_code, request=request))


@pytest.mark.parametrize("path", ALL_STAGE_PATHS)
def test_every_stage_endpoint_rejects_a_wrong_internal_key(client, path):
    response = client.post(path, json={}, headers={INTERNAL_API_KEY_HEADER: INVALID_INTERNAL_API_KEY})

    assert response.status_code == HTTP_UNAUTHORIZED


@pytest.mark.parametrize("path", ALL_STAGE_PATHS)
def test_every_stage_endpoint_requires_the_internal_key_header(client, path):
    response = client.post(path, json={})

    assert response.status_code != HTTP_OK


def test_the_youtube_stage_returns_video_facts_and_the_precheck_identification(client, mocker):
    _patch_identification(mocker)

    response = client.post("/stages/youtube-metadata", json={"youtubeId": VIDEO_ID}, headers=_headers())

    assert response.status_code == HTTP_OK
    assert response.json() == {
        "outcome": "IDENTIFIED",
        "video": {
            "videoTitle": "Test Song (Official Video)",
            "channelTitle": "Test Artist",
            "durationSeconds": DURATION_SECONDS,
            "categoryId": "10",
        },
        "title": "Test Song",
        "artists": ["Test Artist"],
        "featuredArtists": ["Guest"],
        "color": "8B5CF6",
        "rejectionReason": None,
        "rejectionDetail": None,
        "result": None,
    }


def test_a_precheck_rejection_is_a_successful_response_with_the_reason(client, mocker):
    _patch_identification(mocker, precheck=_precheck(is_compilation=True))

    response = client.post("/stages/youtube-metadata", json={"youtubeId": VIDEO_ID}, headers=_headers())

    assert response.status_code == HTTP_OK
    body = response.json()
    assert body["outcome"] == "REJECTED"
    assert body["rejectionReason"] == "COMPILATION"
    assert body["rejectionDetail"]
    assert body["result"]["status"] == service.REJECTED_STATUS


def test_the_hard_filter_rejects_without_calling_the_precheck(client, mocker):
    precheck_mock = _patch_identification(
        mocker, youtube_data=_youtube_data(category_id=NON_MUSIC_CATEGORY, duration_seconds=LONG_VIDEO_SECONDS)
    )

    response = client.post("/stages/youtube-metadata", json={"youtubeId": VIDEO_ID}, headers=_headers())

    assert response.json()["outcome"] == "REJECTED"
    assert response.json()["rejectionReason"] == "NOT_MUSIC"
    precheck_mock.assert_not_called()


def test_a_verified_duplicate_finishes_the_run_with_its_answer(client, mocker):
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
    _patch_identification(mocker, duplicate=duplicate)

    body = client.post("/stages/youtube-metadata", json={"youtubeId": VIDEO_ID}, headers=_headers()).json()

    assert body["outcome"] == "DUPLICATE"
    assert body["result"]["content"]["canonical_song_id"] == MATCHED_SONG_ID
    assert body["result"]["content"]["duration_seconds"] == DURATION_SECONDS


def _error_body(response):
    body = response.json()
    assert set(body) == {"stage", "provider", "providerHint", "cause", "upstreamStatus", "message"}
    return body


@respx.mock
def test_a_video_youtube_does_not_know_is_no_usable_metadata(client):
    respx.get(url__startswith=YOUTUBE_VIDEOS_URL).mock(return_value=httpx.Response(HTTP_OK, json={"items": []}))

    response = client.post("/stages/youtube-metadata", json={"youtubeId": VIDEO_ID}, headers=_headers())

    assert response.status_code == HTTP_UNPROCESSABLE
    assert _error_body(response) | {"message": None} == {
        "stage": "YOUTUBE_METADATA",
        "provider": "YOUTUBE",
        "providerHint": "YOUTUBE_VIDEO_LOOKUP",
        "cause": "NO_USABLE_METADATA",
        "upstreamStatus": None,
        "message": None,
    }


def test_an_unparseable_video_id_is_no_usable_metadata(client):
    response = client.post("/stages/youtube-metadata", json={"youtubeId": "not a video"}, headers=_headers())

    assert response.status_code == HTTP_UNPROCESSABLE
    assert _error_body(response)["cause"] == "NO_USABLE_METADATA"


@pytest.mark.parametrize(
    ("upstream_error", "expected_status", "expected_cause", "expected_upstream_status"),
    [
        (_status_error(HTTP_FORBIDDEN), HTTP_BAD_GATEWAY, "HTTP_ERROR", HTTP_FORBIDDEN),
        (httpx.ReadTimeout("timed out"), HTTP_GATEWAY_TIMEOUT, "TIMEOUT", None),
        (httpx.ConnectError("refused"), HTTP_SERVICE_UNAVAILABLE, "CONNECTION_ERROR", None),
        (ValueError("bad payload"), HTTP_INTERNAL_ERROR, "OTHER", None),
    ],
)
def test_youtube_lookup_failures_map_to_an_error_body(client, mocker, upstream_error, expected_status, expected_cause, expected_upstream_status):
    mocker.patch.object(youtube, "_fetch_video_metadata", side_effect=upstream_error)

    response = client.post("/stages/youtube-metadata", json={"youtubeId": VIDEO_ID}, headers=_headers())

    assert response.status_code == expected_status
    body = _error_body(response)
    assert body["provider"] == "YOUTUBE"
    assert body["cause"] == expected_cause
    assert body["upstreamStatus"] == expected_upstream_status


def _openai_status_error(status_code):
    request = httpx.Request("POST", "https://api.deepinfra.com/v1/openai/chat/completions")
    return openai.APIStatusError("rejected", response=httpx.Response(status_code, request=request), body=None)


@pytest.mark.parametrize(
    ("precheck_error", "expected_status", "expected_cause", "expected_upstream_status"),
    [
        (_openai_status_error(HTTP_UNAUTHORIZED), HTTP_BAD_GATEWAY, "HTTP_ERROR", HTTP_UNAUTHORIZED),
        (openai.APITimeoutError(request=httpx.Request("POST", "https://api.deepinfra.com")), HTTP_GATEWAY_TIMEOUT, "TIMEOUT", None),
        (openai.APIConnectionError(request=httpx.Request("POST", "https://api.deepinfra.com")), HTTP_SERVICE_UNAVAILABLE, "CONNECTION_ERROR", None),
    ],
)
def test_precheck_failures_are_attributed_to_deepinfra(client, mocker, precheck_error, expected_status, expected_cause, expected_upstream_status):
    precheck_mock = _patch_identification(mocker)
    precheck_mock.side_effect = precheck_error

    response = client.post("/stages/youtube-metadata", json={"youtubeId": VIDEO_ID}, headers=_headers())

    assert response.status_code == expected_status
    body = _error_body(response)
    assert body["provider"] == "DEEPINFRA"
    assert body["providerHint"] == "API_KEY_PROVIDER"
    assert body["cause"] == expected_cause
    assert body["upstreamStatus"] == expected_upstream_status


@respx.mock
def test_an_upstream_error_leaks_no_key_or_url_into_the_body_or_the_formatted_logs(client, caplog):
    respx.get(url__startswith=YOUTUBE_VIDEOS_URL).mock(return_value=httpx.Response(HTTP_FORBIDDEN, json={"error": "quota"}))

    with caplog.at_level(logging.INFO):
        response = client.post("/stages/youtube-metadata", json={"youtubeId": VIDEO_ID}, headers=_headers())

    assert response.status_code == HTTP_BAD_GATEWAY
    assert settings.youtube_api_key not in response.text
    assert "googleapis" not in response.text
    assert "quota" not in response.text
    formatter = JsonLineFormatter()
    formatted_logs = "\n".join(formatter.format(record) for record in caplog.records)
    assert "googleapis" in formatted_logs
    assert settings.youtube_api_key not in formatted_logs


@respx.mock
def test_a_discogs_error_carries_its_status_and_the_key_provider_hint(client):
    respx.get(url__startswith=DISCOGS_SEARCH_URL).mock(return_value=httpx.Response(HTTP_UNAUTHORIZED))

    response = client.post("/stages/discogs", json=SOURCE_BODY, headers=_headers())

    assert response.status_code == HTTP_BAD_GATEWAY
    assert _error_body(response) | {"message": None} == {
        "stage": "DISCOGS",
        "provider": "DISCOGS",
        "providerHint": "API_KEY_PROVIDER",
        "cause": "HTTP_ERROR",
        "upstreamStatus": HTTP_UNAUTHORIZED,
        "message": None,
    }
    assert settings.discogs_consumer_secret not in response.text


@pytest.mark.parametrize(
    ("path", "source_module", "stage", "provider"),
    [
        ("/stages/musicbrainz", musicbrainz, "MUSICBRAINZ", "MUSICBRAINZ"),
        ("/stages/wikidata", wikidata, "WIKIDATA", "WIKIDATA"),
        ("/stages/wikipedia", wikipedia, "WIKIPEDIA", "WIKIPEDIA"),
    ],
)
def test_keyless_source_failures_carry_the_keyless_hint(client, mocker, path, source_module, stage, provider):
    mocker.patch.object(source_module, "search_or_raise", side_effect=httpx.ConnectTimeout("timed out"))

    response = client.post(path, json=SOURCE_BODY, headers=_headers())

    assert response.status_code == HTTP_GATEWAY_TIMEOUT
    body = _error_body(response)
    assert (body["stage"], body["provider"], body["providerHint"], body["cause"]) == (
        stage,
        provider,
        "KEYLESS_PROVIDER",
        "TIMEOUT",
    )


def test_a_structured_source_stage_returns_candidates_and_the_earliest_year(client, mocker):
    candidates = [
        {"query": "track", "title": "Test Song", "date": "2001-03-01"},
        {"query": "album", "title": "Test Album", "date": f"{LOCKED_YEAR}-11-01"},
    ]
    search_mock = mocker.patch.object(musicbrainz, "search_or_raise", return_value=candidates)

    response = client.post("/stages/musicbrainz", json=SOURCE_BODY, headers=_headers())

    assert response.status_code == HTTP_OK
    assert response.json() == {"candidates": candidates, "earliestYear": LOCKED_YEAR}
    search_mock.assert_called_once_with("Test Song", "Test Artist")


@pytest.mark.parametrize("path", ["/stages/musicbrainz", "/stages/discogs", "/stages/wikidata"])
def test_a_structured_source_with_no_candidates_reports_a_null_earliest_year(client, mocker, path):
    for source_module in (musicbrainz, discogs, wikidata):
        mocker.patch.object(source_module, "search_or_raise", return_value=[])

    body = client.post(path, json=SOURCE_BODY, headers=_headers()).json()

    assert body["candidates"] == []
    assert "earliestYear" in body
    assert body["earliestYear"] is None


def test_the_wikidata_stage_reports_the_track_sitelinks(client, mocker):
    candidates = [{"query": "track", "entity_id": "Q5", "description": "song", "date": f"+{LOCKED_YEAR}-00-00T00:00:00Z"}]
    mocker.patch.object(wikidata, "search_or_raise", return_value=candidates)
    sitelinks_mock = mocker.patch.object(wikidata, "get_sitelinks_count", return_value=SITELINKS_COUNT)

    body = client.post("/stages/wikidata", json=SOURCE_BODY, headers=_headers()).json()

    assert body == {"candidates": candidates, "earliestYear": LOCKED_YEAR, "sitelinksCount": SITELINKS_COUNT}
    sitelinks_mock.assert_called_once_with("Q5")


def test_the_wikipedia_stage_returns_the_lead_entries(client, mocker):
    entries = [{"query": "track", "page_title": "Test Song (song)", "extract": "Lead text."}]
    mocker.patch.object(wikipedia, "search_or_raise", return_value=entries)

    body = client.post("/stages/wikipedia", json=SOURCE_BODY, headers=_headers()).json()

    assert body == {"candidates": entries, "earliestYear": None}


def _reconcile_body(year_by_source):
    return {
        "title": "Test Song",
        "artists": ["Test Artist"],
        "featuredArtists": ["Guest"],
        "color": "8B5CF6",
        "video": {"videoTitle": "Test Song", "channelTitle": "Test Artist", "durationSeconds": DURATION_SECONDS, "categoryId": "10"},
        "musicbrainzCandidates": [{"date": f"{year_by_source['musicbrainz']}-01-01"}],
        "discogsCandidates": [{"year": year_by_source["discogs"]}],
        "wikidataCandidates": [{"date": f"+{year_by_source['wikidata']}-00-00T00:00:00Z"}],
        "wikipediaCandidates": [],
        "sitelinksCount": SITELINKS_COUNT,
    }


def test_llm_reconcile_locks_three_agreeing_years_without_calling_any_llm(client, mocker):
    extraction_mock = mocker.patch("app.metadata.verification.extract_structured")
    reconciliation_mock = mocker.patch("app.metadata.verification.synthesize_with_model")

    response = client.post(
        "/stages/llm-reconcile",
        json=_reconcile_body({"musicbrainz": LOCKED_YEAR, "discogs": LOCKED_YEAR, "wikidata": LOCKED_YEAR}),
        headers=_headers(),
    )

    assert response.status_code == HTTP_OK
    body = response.json()
    assert body["status"] == service.SUCCESS_STATUS
    assert body["content"]["source"] == service.LOCKED_SOURCE_LABEL
    assert body["content"]["release_year"] == LOCKED_YEAR
    assert body["content"]["verification_status"] == "VERIFIED"
    assert body["content"]["featured_artists"] == ["Guest"]
    assert body["content"]["sitelinks_count"] == SITELINKS_COUNT
    assert body["content"]["duration_seconds"] == DURATION_SECONDS
    extraction_mock.assert_not_called()
    reconciliation_mock.assert_not_called()


def test_an_unexpected_reconcile_failure_is_an_error_body_without_a_provider(client, mocker):
    mocker.patch.object(service, "evaluate_lock", side_effect=RuntimeError("bug"))

    response = client.post(
        "/stages/llm-reconcile",
        json=_reconcile_body({"musicbrainz": LOCKED_YEAR, "discogs": LOCKED_YEAR, "wikidata": LOCKED_YEAR}),
        headers=_headers(),
    )

    assert response.status_code == HTTP_INTERNAL_ERROR
    body = _error_body(response)
    assert (body["stage"], body["provider"], body["cause"]) == ("LLM_RECONCILIATION", None, "OTHER")
    assert "bug" not in response.text


@pytest.mark.parametrize("failing_call,provider", [("extract_structured", "DEEPINFRA"), ("synthesize_with_model", "OPENAI")])
def test_reconcile_provider_failures_propagate_for_stages_but_remain_best_effort_for_resolve(
    client, mocker, failing_call, provider
):
    from app.metadata.schemas import LlmExtractionResult

    extraction = LlmExtractionResult(
        title="Test Song", artist="Test Artist", release_year=WIKIPEDIA_YEAR,
        confidence="medium", reasoning="Source evidence."
    )
    mocker.patch("app.metadata.verification.extract_structured", return_value=extraction)
    mocker.patch("app.metadata.verification.synthesize_with_model", return_value=extraction)
    mocker.patch(f"app.metadata.verification.{failing_call}", side_effect=_openai_status_error(HTTP_UNAUTHORIZED))
    body = _reconcile_body({"musicbrainz": LOCKED_YEAR, "discogs": DIFFERING_YEAR, "wikidata": DIFFERING_YEAR})

    response = client.post("/stages/llm-reconcile", json=body, headers=_headers())

    assert response.status_code == HTTP_BAD_GATEWAY
    error = _error_body(response)
    assert error["provider"] == provider
    assert error["providerHint"] == "API_KEY_PROVIDER"
    assert error["upstreamStatus"] == HTTP_UNAUTHORIZED
    result = service.run_reconciliation_stage(
        body["title"], body["artists"], body["featuredArtists"], body["color"],
        body["musicbrainzCandidates"], body["discogsCandidates"], body["wikidataCandidates"],
        body["wikipediaCandidates"], body["sitelinksCount"],
    )
    assert result is not None


@respx.mock
def test_youtube_metadata_obeys_a_persisted_cooldown_before_fetching(client, monkeypatch):
    monkeypatch.setattr(pace_state, "shared_store", CappedStore(OVER_CAP_WAIT_SECONDS))
    youtube_route = respx.get(url__startswith=YOUTUBE_VIDEOS_URL).mock(return_value=httpx.Response(HTTP_OK))

    response = client.post("/stages/youtube-metadata", json={"youtubeId": VIDEO_ID}, headers=_headers())

    assert response.status_code == HTTP_TOO_MANY_REQUESTS
    assert response.json()["retryAfterSeconds"] == OVER_CAP_RETRY_AFTER_SECONDS
    assert not youtube_route.called


class CappedStore:
    """A pace store whose next free slot is a fixed number of seconds away."""

    def __init__(self, wait_seconds):
        self.wait_seconds = wait_seconds
        self.maximum_waits: list[float | None] = []

    def reserve_slot(self, stage, interval_seconds, maximum_wait_seconds):
        self.maximum_waits.append(maximum_wait_seconds)
        if maximum_wait_seconds is not None and self.wait_seconds > maximum_wait_seconds:
            raise PaceWaitTooLong(self.wait_seconds, stage)
        return ReservedSlot(slot_at=None, wait_seconds=self.wait_seconds)

    def hold(self, stage, pause_seconds):
        return None

    def record_response_time(self, stage, elapsed_seconds):
        return None


@respx.mock
def test_a_wait_over_the_cap_returns_429_with_retry_after_and_sends_nothing(client, monkeypatch):
    store = CappedStore(OVER_CAP_WAIT_SECONDS)
    monkeypatch.setattr(pace_state, "shared_store", store)
    musicbrainz_route = respx.get(url__startswith=MUSICBRAINZ_URL).mock(
        return_value=httpx.Response(HTTP_OK, json={"release-groups": []})
    )

    response = client.post("/stages/musicbrainz", json=SOURCE_BODY, headers=_headers())

    assert response.status_code == HTTP_TOO_MANY_REQUESTS
    body = response.json()
    assert body["stage"] == STAGE_MUSICBRAINZ
    assert body["retryAfterSeconds"] == OVER_CAP_RETRY_AFTER_SECONDS
    assert response.headers["Retry-After"] == str(OVER_CAP_RETRY_AFTER_SECONDS)
    assert store.maximum_waits == [MAXIMUM_STAGE_PACE_WAIT_SECONDS]
    assert not musicbrainz_route.called


@respx.mock
def test_a_wait_under_the_cap_is_slept_and_the_stage_proceeds(client, monkeypatch, mocker):
    monkeypatch.setattr(pace_state, "shared_store", CappedStore(SUB_SECOND_WAIT_SECONDS))
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get(url__startswith=MUSICBRAINZ_URL).mock(return_value=httpx.Response(HTTP_OK, json={"release-groups": []}))

    response = client.post("/stages/musicbrainz", json=SOURCE_BODY, headers=_headers())

    assert response.status_code == HTTP_OK
    sleep.assert_called_once_with(SUB_SECOND_WAIT_SECONDS)


def test_a_retry_after_is_never_below_one_second(client, mocker):
    mocker.patch.object(
        musicbrainz, "search_or_raise", side_effect=PaceWaitTooLong(SUB_SECOND_WAIT_SECONDS, STAGE_MUSICBRAINZ)
    )

    response = client.post("/stages/musicbrainz", json=SOURCE_BODY, headers=_headers())

    assert response.status_code == HTTP_TOO_MANY_REQUESTS
    assert response.json()["retryAfterSeconds"] == MINIMUM_RETRY_AFTER_SECONDS


def test_an_over_cap_wait_inside_the_sitelinks_lookup_still_returns_429(client, mocker):
    candidates = [{"query": "track", "entity_id": "Q5", "description": "song", "date": None}]
    mocker.patch.object(wikidata, "search_or_raise", return_value=candidates)
    mocker.patch.object(wikidata, "_get", side_effect=PaceWaitTooLong(OVER_CAP_WAIT_SECONDS, "WIKIDATA"))

    response = client.post("/stages/wikidata", json=SOURCE_BODY, headers=_headers())

    assert response.status_code == HTTP_TOO_MANY_REQUESTS


@respx.mock
def test_resolve_sleeps_through_a_wait_over_the_cap_instead_of_returning_429(monkeypatch, mocker):
    store = CappedStore(OVER_CAP_WAIT_SECONDS)
    monkeypatch.setattr(pace_state, "shared_store", store)
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get(url__startswith=MUSICBRAINZ_URL).mock(return_value=httpx.Response(HTTP_OK, json={"release-groups": []}))
    _patch_identification(mocker)
    for source_module in (discogs, wikidata, wikipedia):
        mocker.patch.object(source_module, "search_or_raise", return_value=[])

    response = service.resolve_metadata(f"https://www.youtube.com/watch?v={VIDEO_ID}")

    assert response.status == service.SUCCESS_STATUS
    assert store.maximum_waits == [None]
    sleep.assert_called_once_with(OVER_CAP_WAIT_SECONDS)
