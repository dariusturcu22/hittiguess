import json
from pathlib import Path

import httpx
import pytest
import respx

from app.metadata.sources import discogs

DISCOGS_FIXTURES = Path(__file__).parent / "fixtures" / "discogs"


def _release(title, year=None, master_id=None, artist=None, format_list=None):
    release = {"title": title}
    if year is not None:
        release["year"] = year
    if master_id is not None:
        release["master_id"] = master_id
    if artist is not None:
        release["artist"] = artist
    if format_list is not None:
        release["format"] = format_list
    return release


def test_master_year_returns_year_when_present():
    assert discogs.master_year({"year": 1999}) == 1999


def test_master_year_treats_zero_as_unknown():
    assert discogs.master_year({"year": 0}) is None


def test_master_year_returns_none_when_absent():
    assert discogs.master_year({}) is None


def test_masterless_release_years_returns_title_year_pairs():
    releases = [_release("Test Song", year="1998")]
    assert discogs.masterless_release_years(releases) == [{"title": "Test Song", "year": 1998}]


def test_masterless_release_years_excludes_releases_with_a_master():
    releases = [_release("Test Song", year="1998", master_id=100)]
    assert discogs.masterless_release_years(releases) == []


def test_masterless_release_years_excludes_releases_with_no_year():
    releases = [_release("Test Song")]
    assert discogs.masterless_release_years(releases) == []


def test_masterless_release_years_skips_unparseable_year():
    releases = [_release("Test Song", year="unknown")]
    assert discogs.masterless_release_years(releases) == []


def _discogs_response(request: httpx.Request) -> httpx.Response:
    if str(request.url).startswith(discogs.SEARCH_URL):
        if request.url.params.get("release_title") == "Test Album":
            return httpx.Response(
                200,
                json={"results": [_release("Test Artist - Test Album", year="2003", master_id=20)]},
            )
        return httpx.Response(
            200,
            json={
                "results": [
                    _release("Test Artist - Test Song (Remix)", master_id=10),
                    _release("Test Artist - Test Song (Bootleg)", year="1998"),
                ]
            },
        )

    if "/masters/" in str(request.url):
        master_id = str(request.url).rsplit("/", 1)[-1]
        year_by_master_id = {"10": 2015, "20": 2001}
        return httpx.Response(200, json={"title": f"Master {master_id}", "year": year_by_master_id[master_id]})

    return httpx.Response(400)


@respx.mock
def test_search_combines_track_and_album_candidates(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get(url__regex=r"api\.discogs\.com/").mock(side_effect=_discogs_response)

    candidates = discogs.search("Test Song", ["Test Artist"], album="Test Album")

    track_candidates = [candidate for candidate in candidates if candidate["query"] == "track"]
    album_candidates = [candidate for candidate in candidates if candidate["query"] == "album"]
    assert {"query": "track", "title": "Test Song (Remix)", "year": 2015, "kind": "single"} in track_candidates
    assert not any(candidate.get("title") == "Test Song (Bootleg)" for candidate in track_candidates)
    assert album_candidates == [{"query": "album", "title": "Test Album", "year": 2003, "kind": "album"}]


@respx.mock
def test_search_keeps_masterless_candidates_only_when_no_master_exists(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get(discogs.SEARCH_URL).mock(
        return_value=httpx.Response(
            200,
            json={"results": [_release("Test Artist - Niche Song", year="2005")]},
        )
    )

    candidates = discogs.search("Niche Song", ["Test Artist"])

    assert candidates == [{"query": "track", "title": "Test Artist - Niche Song", "year": 2005}]


@respx.mock
def test_search_skips_album_query_when_no_album_given(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get(url__regex=r"api\.discogs\.com/").mock(side_effect=_discogs_response)

    candidates = discogs.search("Test Song", ["Test Artist"])

    assert all(candidate["query"] == "track" for candidate in candidates)


@respx.mock
def test_search_returns_empty_list_on_request_failure(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get(url__regex=r"api\.discogs\.com/").mock(return_value=httpx.Response(500))

    assert discogs.search("Test Song", ["Test Artist"]) == []


@respx.mock
def test_search_release_sends_authenticated_query(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    route = respx.get(discogs.SEARCH_URL).mock(return_value=httpx.Response(200, json={"results": []}))

    discogs.search_release("Test Song", ["Test Artist"])

    request = route.calls.last.request
    assert request.url.params["artist"] == "Test Artist"
    assert request.url.params["track"] == "Test Song"
    assert request.url.params["type"] == "release"
    assert "q" not in request.url.params
    assert request.headers["Authorization"] == discogs._auth_header()


@respx.mock
def test_discogs_cheap_thrills_sia_drops_unrelated_artist_and_old_year(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    releases_json = json.loads((DISCOGS_FIXTURES / "cheap-thrills-releases.json").read_text(encoding="utf-8"))
    masters_json = json.loads((DISCOGS_FIXTURES / "cheap-thrills-masters.json").read_text(encoding="utf-8"))

    def _mock_response(request: httpx.Request) -> httpx.Response:
        if str(request.url).startswith(discogs.SEARCH_URL):
            assert request.url.params["artist"] == "Sia"
            assert request.url.params["track"] == "Cheap Thrills"
            return httpx.Response(200, json=releases_json)
        if "/masters/" in str(request.url):
            master_id = str(request.url).rsplit("/", 1)[-1]
            return httpx.Response(200, json=masters_json[master_id])
        return httpx.Response(404)

    respx.get(url__regex=r"api\.discogs\.com/").mock(side_effect=_mock_response)

    candidates = discogs.search("Cheap Thrills", ["Sia"])
    assert len(candidates) == 1
    assert candidates[0]["year"] == 2016
    assert not any(candidate["year"] == 1968 for candidate in candidates)


@respx.mock
def test_discogs_con_calma_drops_various_and_discards_masterless_candidates(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    releases_json = json.loads((DISCOGS_FIXTURES / "con-calma-releases.json").read_text(encoding="utf-8"))
    masters_json = json.loads((DISCOGS_FIXTURES / "con-calma-masters.json").read_text(encoding="utf-8"))

    def _mock_response(request: httpx.Request) -> httpx.Response:
        if str(request.url).startswith(discogs.SEARCH_URL):
            assert request.url.params["artist"] == "Daddy Yankee"
            assert request.url.params["track"] == "Con Calma"
            return httpx.Response(200, json=releases_json)
        if "/masters/" in str(request.url):
            master_id = str(request.url).rsplit("/", 1)[-1]
            return httpx.Response(200, json=masters_json[master_id])
        return httpx.Response(404)

    respx.get(url__regex=r"api\.discogs\.com/").mock(side_effect=_mock_response)

    candidates = discogs.search("Con Calma", ["Daddy Yankee", "Snow"])
    assert len(candidates) == 1
    assert candidates[0]["year"] == 2019
    assert not any("various" in str(candidate.get("title", "")).lower() for candidate in candidates)


def _rate_limit_headers(limit: int, remaining: int) -> dict[str, str]:
    return {"X-Discogs-Ratelimit": str(limit), "X-Discogs-Ratelimit-Remaining": str(remaining)}


def test_rate_limiter_spaces_consecutive_calls_by_the_current_delay(mocker):
    mocker.patch("app.metadata.sources.pacing.time.monotonic", return_value=100.0)
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")
    limiter = discogs.DiscogsRateLimiter()

    limiter.wait()
    limiter.wait()

    sleep.assert_called_once()
    (slept_seconds,), unused_keywords = sleep.call_args
    assert slept_seconds == pytest.approx(limiter.delay_seconds)


def test_a_usage_breach_slows_down_and_holds_every_caller_for_the_cooldown(mocker):
    mocker.patch("app.metadata.sources.pacing.time.monotonic", return_value=100.0)
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")
    limiter = discogs.DiscogsRateLimiter(target_utilization=0.5)
    starting_delay = limiter.delay_seconds

    response = httpx.Response(200, headers=_rate_limit_headers(limit=60, remaining=0))
    limiter.record_response(response)
    limiter.wait()

    assert limiter.delay_seconds > starting_delay
    (slept_seconds,), unused_keywords = sleep.call_args
    assert slept_seconds == pytest.approx(discogs.BREACH_COOLDOWN_SECONDS)


def test_rate_limiter_eases_toward_minimum_when_usage_is_well_under_target(mocker):
    limiter = discogs.DiscogsRateLimiter(target_utilization=0.5)
    limiter.delay_seconds = 2.0

    response = httpx.Response(200, headers=_rate_limit_headers(limit=60, remaining=59))
    limiter.record_response(response)

    assert limiter.delay_seconds < 2.0
    assert limiter.delay_seconds >= discogs.MIN_DELAY_SECONDS


def test_rate_limiter_holds_steady_within_the_target_band(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    limiter = discogs.DiscogsRateLimiter(target_utilization=0.5)
    starting_delay = limiter.delay_seconds

    response = httpx.Response(200, headers=_rate_limit_headers(limit=60, remaining=30))
    limiter.record_response(response)

    assert limiter.delay_seconds == starting_delay


def test_rate_limiter_ignores_response_with_no_rate_limit_headers(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    limiter = discogs.DiscogsRateLimiter()
    starting_delay = limiter.delay_seconds

    limiter.record_response(httpx.Response(200))

    assert limiter.delay_seconds == starting_delay
    assert limiter.last_observed_utilization is None


def test_rate_limiter_ignores_response_with_zero_limit(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    limiter = discogs.DiscogsRateLimiter()
    starting_delay = limiter.delay_seconds

    response = httpx.Response(200, headers=_rate_limit_headers(limit=0, remaining=0))
    limiter.record_response(response)

    assert limiter.delay_seconds == starting_delay
    assert limiter.last_observed_utilization is None


def test_matches_discogs_artist_compares_whole_words():
    assert discogs._is_valid_release({"title": "Sia - Cheap Thrills"}, ["Sia"])
    assert discogs._is_valid_release({"title": "Sia Feat. Sean Paul - Cheap Thrills"}, ["Sia"])
    assert discogs._is_valid_release({"title": "Daddy Yankee Feat. Snow (2) - Con Calma"}, ["Daddy Yankee", "Snow"])
    assert not discogs._is_valid_release({"title": "Siamese Dream - Disarm"}, ["Sia"])
    assert not discogs._is_valid_release({"title": "Sian Evans - Hide U"}, ["Sia"])


@respx.mock
def test_an_album_takes_the_year_of_the_edition_that_contains_the_track(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    deluxe_edition = _release("Beyonce - B'Day", year="2007", master_id=30, format_list=["CD", "Album"])
    respx.get(url__regex=r"api\.discogs\.com/database/search").mock(
        return_value=httpx.Response(200, json={"results": [deluxe_edition]}))
    master_route = respx.get(url__regex=r"api\.discogs\.com/masters/")

    candidates = discogs.search("Beautiful Liar", ["Beyonce"])

    assert candidates == [{"query": "track", "title": "B'Day", "year": 2007, "kind": "album"}]
    assert not master_route.called
