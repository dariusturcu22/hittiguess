import httpx
import respx

from app.metadata.sources import musicbrainz


def _release_group(title, artist, date, group_type="Single", score=100):
    return {
        "score": score,
        "title": title,
        "artist-credit": [{"name": artist}],
        "first-release-date": date,
        "primary-type": group_type,
    }


def test_select_best_release_group_prefers_earliest_dated_top_scored_candidate():
    release_groups = [
        _release_group("Test Song", "Test Artist", "2019-01-01"),
        _release_group("Test Song", "Test Artist", "2011-06-15"),
    ]
    best = musicbrainz.select_best_release_group(release_groups)
    assert best["first-release-date"] == "2011-06-15"


def test_select_best_release_group_prefers_matching_type_among_tied_scores():
    release_groups = [
        _release_group("Test Song", "Test Artist", "2015-01-01", group_type="Album"),
        _release_group("Test Song", "Test Artist", "2016-01-01", group_type="Single"),
    ]
    best = musicbrainz.select_best_release_group(release_groups, prefer_type="Single")
    assert best["first-release-date"] == "2016-01-01"


def test_select_best_release_group_ignores_lower_scored_candidates():
    release_groups = [
        _release_group("Test Song", "Test Artist", "1990-01-01", score=50),
        _release_group("Test Song", "Test Artist", "2020-01-01", score=100),
    ]
    best = musicbrainz.select_best_release_group(release_groups)
    assert best["first-release-date"] == "2020-01-01"


def test_select_best_release_group_returns_none_for_empty_list():
    assert musicbrainz.select_best_release_group([]) is None


def _mock_release_group_response(request: httpx.Request) -> httpx.Response:
    query = request.url.params.get("query", "")
    if "Test Album" in query:
        return httpx.Response(200, json={"release-groups": [_release_group("Test Album", "Test Artist", "2001-01-01", "Album")]})
    return httpx.Response(200, json={"release-groups": [_release_group("Test Song", "Test Artist", "1999-05-01")]})


@respx.mock
def test_search_combines_track_and_album_candidates(mocker):
    mocker.patch("app.metadata.sources.musicbrainz.time.sleep")
    respx.get(url__regex=r"musicbrainz\.org/ws/2/release-group/").mock(side_effect=_mock_release_group_response)

    candidates = musicbrainz.search("Test Song", "Test Artist", album="Test Album")

    track_candidates = [candidate for candidate in candidates if candidate["query"] == "track"]
    album_candidates = [candidate for candidate in candidates if candidate["query"] == "album"]
    assert track_candidates[0]["date"] == "1999-05-01"
    assert album_candidates[0]["date"] == "2001-01-01"


@respx.mock
def test_search_skips_album_query_when_no_album_given(mocker):
    mocker.patch("app.metadata.sources.musicbrainz.time.sleep")
    respx.get(url__regex=r"musicbrainz\.org/ws/2/release-group/").mock(side_effect=_mock_release_group_response)

    candidates = musicbrainz.search("Test Song", "Test Artist")

    assert all(candidate["query"] == "track" for candidate in candidates)


@respx.mock
def test_search_returns_empty_list_on_request_failure(mocker):
    mocker.patch("app.metadata.sources.musicbrainz.time.sleep")
    respx.get(url__regex=r"musicbrainz\.org/ws/2/release-group/").mock(return_value=httpx.Response(500))

    assert musicbrainz.search("Test Song", "Test Artist") == []
