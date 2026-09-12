import httpx
import respx

from app.metadata.sources import discogs


def _release(title, year=None, master_id=None):
    release = {"title": title}
    if year is not None:
        release["year"] = year
    if master_id is not None:
        release["master_id"] = master_id
    return release


def test_find_master_ids_returns_distinct_ids_in_first_seen_order():
    releases = [
        _release("Test Song (Remix)", master_id=200),
        _release("Test Song", master_id=100),
        _release("Test Song (Reissue)", master_id=200),
    ]
    assert discogs.find_master_ids(releases) == [200, 100]


def test_find_master_ids_excludes_releases_with_no_master():
    releases = [_release("Test Song", master_id=0), _release("Test Song")]
    assert discogs.find_master_ids(releases) == []


def test_find_master_ids_caps_at_max_masters():
    releases = [_release(f"Test Song {index}", master_id=index) for index in range(1, 6)]
    assert discogs.find_master_ids(releases, max_masters=2) == [1, 2]


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
        query = request.url.params.get("q", "")
        if "Test Album" in query:
            return httpx.Response(
                200,
                json={"results": [_release("Test Album", master_id=20)]},
            )
        return httpx.Response(
            200,
            json={
                "results": [
                    _release("Test Song (Remix)", master_id=10),
                    _release("Test Song (Bootleg)", year="1998"),
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
    mocker.patch("app.metadata.sources.discogs.time.sleep")
    respx.get(url__regex=r"api\.discogs\.com/").mock(side_effect=_discogs_response)

    candidates = discogs.search("Test Song", "Test Artist", album="Test Album")

    track_candidates = [candidate for candidate in candidates if candidate["query"] == "track"]
    album_candidates = [candidate for candidate in candidates if candidate["query"] == "album"]
    assert {"query": "track", "title": "Master 10", "year": 2015} in track_candidates
    assert {"query": "track", "title": "Test Song (Bootleg)", "year": 1998} in track_candidates
    assert album_candidates == [{"query": "album", "title": "Master 20", "year": 2001}]


@respx.mock
def test_search_skips_album_query_when_no_album_given(mocker):
    mocker.patch("app.metadata.sources.discogs.time.sleep")
    respx.get(url__regex=r"api\.discogs\.com/").mock(side_effect=_discogs_response)

    candidates = discogs.search("Test Song", "Test Artist")

    assert all(candidate["query"] == "track" for candidate in candidates)


@respx.mock
def test_search_returns_empty_list_on_request_failure(mocker):
    mocker.patch("app.metadata.sources.discogs.time.sleep")
    respx.get(url__regex=r"api\.discogs\.com/").mock(return_value=httpx.Response(500))

    assert discogs.search("Test Song", "Test Artist") == []


@respx.mock
def test_search_release_sends_authenticated_query(mocker):
    mocker.patch("app.metadata.sources.discogs.time.sleep")
    route = respx.get(discogs.SEARCH_URL).mock(return_value=httpx.Response(200, json={"results": []}))

    discogs.search_release("Test Song", "Test Artist")

    request = route.calls.last.request
    assert request.url.params["q"] == "Test Artist Test Song"
    assert request.url.params["type"] == "release"
    assert request.headers["Authorization"] == discogs._auth_header()


def _rate_limit_headers(limit: int, remaining: int) -> dict[str, str]:
    return {"X-Discogs-Ratelimit": str(limit), "X-Discogs-Ratelimit-Remaining": str(remaining)}


def test_rate_limiter_wait_sleeps_for_current_delay(mocker):
    sleep = mocker.patch("app.metadata.sources.discogs.time.sleep")
    limiter = discogs.DiscogsRateLimiter()

    limiter.wait()

    sleep.assert_called_once_with(limiter.delay_seconds)


def test_rate_limiter_slows_down_and_cools_off_when_usage_crosses_target(mocker):
    sleep = mocker.patch("app.metadata.sources.discogs.time.sleep")
    limiter = discogs.DiscogsRateLimiter(target_utilization=0.5)
    starting_delay = limiter.delay_seconds

    response = httpx.Response(200, headers=_rate_limit_headers(limit=60, remaining=0))
    limiter.record_response(response)

    assert limiter.delay_seconds > starting_delay
    sleep.assert_called_once_with(discogs.BREACH_COOLDOWN_SECONDS)


def test_rate_limiter_eases_toward_minimum_when_usage_is_well_under_target(mocker):
    mocker.patch("app.metadata.sources.discogs.time.sleep")
    limiter = discogs.DiscogsRateLimiter(target_utilization=0.5)
    limiter.delay_seconds = 2.0

    response = httpx.Response(200, headers=_rate_limit_headers(limit=60, remaining=59))
    limiter.record_response(response)

    assert limiter.delay_seconds < 2.0
    assert limiter.delay_seconds >= discogs.MIN_DELAY_SECONDS


def test_rate_limiter_holds_steady_within_the_target_band(mocker):
    mocker.patch("app.metadata.sources.discogs.time.sleep")
    limiter = discogs.DiscogsRateLimiter(target_utilization=0.5)
    starting_delay = limiter.delay_seconds

    response = httpx.Response(200, headers=_rate_limit_headers(limit=60, remaining=30))
    limiter.record_response(response)

    assert limiter.delay_seconds == starting_delay


def test_rate_limiter_ignores_response_with_no_rate_limit_headers(mocker):
    mocker.patch("app.metadata.sources.discogs.time.sleep")
    limiter = discogs.DiscogsRateLimiter()
    starting_delay = limiter.delay_seconds

    limiter.record_response(httpx.Response(200))

    assert limiter.delay_seconds == starting_delay
    assert limiter.last_observed_utilization is None


def test_rate_limiter_ignores_response_with_zero_limit(mocker):
    mocker.patch("app.metadata.sources.discogs.time.sleep")
    limiter = discogs.DiscogsRateLimiter()
    starting_delay = limiter.delay_seconds

    response = httpx.Response(200, headers=_rate_limit_headers(limit=0, remaining=0))
    limiter.record_response(response)

    assert limiter.delay_seconds == starting_delay
    assert limiter.last_observed_utilization is None
