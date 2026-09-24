import pytest
from fastapi.testclient import TestClient

from app.auth import INTERNAL_API_KEY_HEADER
from app.config import settings
from app.main import app
from app.metadata import fast_tier, service
from app.metadata.schemas import MetadataResolveResponse, SongMetadataResult
from app.metadata.sources import musicbrainz, wikipedia
from app.rate_limit import FAST_TIER_MAX_REQUESTS_PER_WINDOW, fast_tier_rate_limiter

YOUTUBE_URL = "https://youtube.com/watch?v=abc12345678"
TOO_MANY_REQUESTS_STATUS_CODE = 429
OK_STATUS_CODE = 200


@pytest.fixture(autouse=True)
def fresh_dispatcher_and_limiter(mocker):
    mocker.patch.object(fast_tier, "lane_dispatcher", fast_tier.LaneDispatcher(fast_tier.EXPECTED_SECONDS_PER_SONG))
    fast_tier_rate_limiter.reset()
    yield
    fast_tier_rate_limiter.reset()


def test_an_idle_dispatcher_prefers_the_faster_lane():
    dispatcher = fast_tier.LaneDispatcher(fast_tier.EXPECTED_SECONDS_PER_SONG)

    assert dispatcher.lane_order()[0] == fast_tier.MUSICBRAINZ_LANE


def test_a_busy_lane_hands_the_next_song_to_the_lane_that_frees_up_first():
    dispatcher = fast_tier.LaneDispatcher(fast_tier.EXPECTED_SECONDS_PER_SONG)

    with dispatcher.occupy(fast_tier.MUSICBRAINZ_LANE), dispatcher.occupy(fast_tier.MUSICBRAINZ_LANE):
        assert dispatcher.lane_order()[0] == fast_tier.WIKIPEDIA_LANE
    assert dispatcher.lane_order()[0] == fast_tier.MUSICBRAINZ_LANE


def test_date_fast_answers_from_one_lane_only(mocker):
    musicbrainz_search = mocker.patch.object(musicbrainz, "search", return_value=[{"date": "1999-05-01"}])
    wikipedia_search = mocker.patch.object(wikipedia, "search")

    result = fast_tier.date_fast("Test Song", ["Test Artist"])

    assert result.release_year == 1999
    assert result.lane == fast_tier.MUSICBRAINZ_LANE
    assert result.source == "fast-tier-musicbrainz"
    assert result.confidence == "low"
    musicbrainz_search.assert_called_once_with("Test Song", "Test Artist")
    wikipedia_search.assert_not_called()


def test_date_fast_falls_back_to_the_other_lane_when_the_first_finds_nothing(mocker):
    mocker.patch.object(musicbrainz, "search", return_value=[])
    mocker.patch.object(wikipedia, "search", return_value=[{"query": "track", "page_title": "Test Song", "extract": "Released in 2004."}])
    extraction = mocker.Mock(release_year=2004)
    mocker.patch.object(fast_tier, "_run_wikipedia_extraction", return_value=extraction)

    result = fast_tier.date_fast("Test Song", ["Test Artist"])

    assert result.release_year == 2004
    assert result.lane == fast_tier.WIKIPEDIA_LANE


def test_date_fast_reports_no_answer_when_both_lanes_come_up_empty(mocker):
    mocker.patch.object(musicbrainz, "search", side_effect=RuntimeError("down"))
    mocker.patch.object(wikipedia, "search", return_value=[])

    result = fast_tier.date_fast("Test Song", ["Test Artist"])

    assert result.release_year is None
    assert result.lane is None


def test_identify_returns_the_clean_song_without_dating_it(mocker):
    mocker.patch.object(
        fast_tier,
        "identify_submission",
        return_value=service.IdentifiedSubmission(title="Test Song", main_artists=["Test Artist"], featured_artists=[], color="8B5CF6"),
    )

    result = fast_tier.identify(YOUTUBE_URL)

    assert result.status == "SUCCESS"
    assert result.identified.title == "Test Song"
    assert result.duplicate is None


def test_identify_passes_a_verified_duplicate_through_whole(mocker):
    duplicate = SongMetadataResult(
        title="Test Song", release_year=1999, color="8B5CF6", confidence="high", source=service.DUPLICATE_MATCH_SOURCE_LABEL, reasoning="match"
    )
    mocker.patch.object(
        fast_tier, "identify_submission", return_value=MetadataResolveResponse(status="SUCCESS", model="model", content=duplicate)
    )

    result = fast_tier.identify(YOUTUBE_URL)

    assert result.identified is None
    assert result.duplicate.release_year == 1999


def test_identify_passes_a_rejection_through(mocker):
    mocker.patch.object(
        fast_tier,
        "identify_submission",
        return_value=MetadataResolveResponse(status="REJECTED", model="model", rejection_reason="NOT_MUSIC", rejection_detail="Not a song"),
    )

    result = fast_tier.identify(YOUTUBE_URL)

    assert result.status == "REJECTED"
    assert result.rejection_reason == "NOT_MUSIC"


def test_identify_reports_an_error_instead_of_raising(mocker):
    mocker.patch.object(fast_tier, "identify_submission", side_effect=RuntimeError("youtube down"))

    assert fast_tier.identify(YOUTUBE_URL).status == "ERROR"


def test_the_fast_endpoints_have_their_own_higher_rate_limit(mocker):
    mocker.patch("app.metadata.fast_tier_router.date_fast", return_value=fast_tier.FastDateResponse(release_year=1999, confidence="low", source="fast-tier-musicbrainz", lane="musicbrainz"))
    client = TestClient(app)
    headers = {INTERNAL_API_KEY_HEADER: settings.internal_service_api_key}
    body = {"title": "Test Song", "main_artists": ["Test Artist"]}

    statuses = [client.post("/metadata/date-fast", json=body, headers=headers).status_code for unused_index in range(FAST_TIER_MAX_REQUESTS_PER_WINDOW)]
    over_limit_status = client.post("/metadata/date-fast", json=body, headers=headers).status_code

    assert set(statuses) == {OK_STATUS_CODE}
    assert over_limit_status == TOO_MANY_REQUESTS_STATUS_CODE
