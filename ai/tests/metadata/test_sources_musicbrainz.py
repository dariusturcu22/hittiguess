import json
from pathlib import Path

import httpx
import pytest
import respx

from app.metadata import service
from app.metadata.sources import discogs, musicbrainz, wikidata
from app.metadata.verification import _extract_earliest_year

FIXTURES = Path(__file__).parent / "fixtures" / "musicbrainz"

SONG_FIXTURES = (
    pytest.param(
        "hey-mama",
        "Hey Mama",
        ["David Guetta", "Nicki Minaj", "Bebe Rexha", "Afrojack"],
        2014,
        id="hey-mama",
    ),
    pytest.param("timber", "Timber", ["Pitbull", "Kesha"], 2013, id="timber"),
    pytest.param(
        "moves-like-jagger",
        "Moves Like Jagger",
        ["Maroon 5", "Christina Aguilera"],
        2011,
        id="moves-like-jagger",
    ),
    pytest.param("desire", "Desire", ["Morris"], 2009, id="desire"),
    pytest.param("try", "Try", ["P!nk"], 2012, id="try"),
)


def _release_group(
    title,
    artist,
    date,
    group_type="Single",
    score=100,
    secondary_types=None,
):
    return {
        "score": score,
        "title": title,
        "artist-credit": [{"name": artist, "artist": {"name": artist}}],
        "first-release-date": date,
        "primary-type": group_type,
        "secondary-types": secondary_types or [],
    }


def _recording(
    score=100,
    title="Test Song",
    artist="Test Artist",
    disambiguation=None,
    releases=None,
):
    return {
        "score": score,
        "title": title,
        "artist-credit": [{"name": artist, "artist": {"name": artist}}],
        "disambiguation": disambiguation,
        "releases": releases or [],
    }


def _release(
    date="2011-09-27",
    status="Official",
    primary_type="Album",
    secondary_types=None,
    release_group_id="group-1",
    release_group_title="Test Album",
    group_first_release_date="2010-01-01",
):
    return {
        "title": release_group_title,
        "status": status,
        "date": date,
        "release-group": {
            "id": release_group_id,
            "title": release_group_title,
            "first-release-date": group_first_release_date,
            "primary-type": primary_type,
            "secondary-types": secondary_types or [],
            "artist-credit": [{"name": "Test Artist", "artist": {"name": "Test Artist"}}],
        },
    }


def _load_fixture(slug: str, kind: str) -> dict:
    path = FIXTURES / f"{slug}-{kind}.json"
    return json.loads(path.read_text(encoding="utf-8"))


def _mock_fixture(slug: str):
    release_group_response = _load_fixture(slug, "release-group")
    recording_response = _load_fixture(slug, "recording")

    def respond(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/release-group/"):
            return httpx.Response(200, json=release_group_response)
        return httpx.Response(200, json=recording_response)

    return respx.get(url__regex=r"musicbrainz\.org/ws/2/(release-group|recording)/").mock(
        side_effect=respond
    )


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


@pytest.mark.parametrize("slug,title,main_artists,expected_year", SONG_FIXTURES)
@respx.mock
def test_real_responses_resolve_to_the_earliest_year(
    slug, title, main_artists, expected_year, mocker
):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    _mock_fixture(slug)

    candidates = musicbrainz.search(title, main_artists)

    assert _extract_earliest_year(candidates) == expected_year


@respx.mock
def test_hey_mama_keeps_the_album_listen_as_its_earliest_candidate(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    _mock_fixture("hey-mama")

    candidates = musicbrainz.search(
        "Hey Mama", ["David Guetta", "Nicki Minaj", "Bebe Rexha", "Afrojack"]
    )

    album_candidate = next(candidate for candidate in candidates if candidate["query"] == "album")
    assert album_candidate["title"] == "Listen"
    assert album_candidate["date"] == "2014"


@respx.mock
def test_timber_uses_the_original_single_instead_of_the_top_scored_remix(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    _mock_fixture("timber")

    candidates = musicbrainz.search("Timber", ["Pitbull", "Kesha"])

    track_candidate = next(candidate for candidate in candidates if candidate["query"] == "track")
    assert track_candidate["title"] == "Timber"
    assert track_candidate["date"] == "2013-10-07"
    assert all(candidate["query"] == "track" for candidate in candidates)


@respx.mock
def test_desire_does_not_use_the_low_scored_1951_release_group(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    _mock_fixture("desire")

    candidates = musicbrainz.search("Desire", ["Morris"])

    assert all(candidate["date"] != "1951-04" for candidate in candidates)


@respx.mock
def test_try_does_not_use_the_different_song_on_the_2003_album(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    _mock_fixture("try")

    candidates = musicbrainz.search("Try", ["P!nk"])

    assert all(candidate["title"] != "Try This" for candidate in candidates)


@respx.mock
def test_sources_query_with_only_the_first_main_artist(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    seen_queries = []

    def respond(request: httpx.Request) -> httpx.Response:
        seen_queries.append(request.url.params.get("query", ""))
        if request.url.path.endswith("/release-group/"):
            return httpx.Response(200, json={"release-groups": []})
        return httpx.Response(200, json={"recordings": []})

    respx.get(url__regex=r"musicbrainz\.org/ws/2/(release-group|recording)/").mock(
        side_effect=respond
    )

    musicbrainz.search("Timber", ["Pitbull", "Kesha"])

    assert len(seen_queries) == 2
    assert all('artist:"Pitbull"' in query for query in seen_queries)
    assert all("Kesha" not in query for query in seen_queries)


@pytest.mark.parametrize(
    "artist_credit,main_artists,expected",
    (
        ([{"name": "P!nk"}], ["P!nk"], True),
        ([{"name": "Beyoncé"}], ["Beyonce"], True),
        ([{"name": "Ke$ha", "artist": {"name": "Kesha"}}], ["Kesha"], True),
        ([{"name": "Morrissey"}], ["Morris"], False),
        ([{"name": "Joe Morris Blues Cavalcade"}], ["Morris"], True),
        (
            [
                {"name": "Pitbull", "artist": {"name": "Pitbull"}},
                {"name": "Ke$ha", "artist": {"name": "Kesha"}},
            ],
            ["Pitbull", "Kesha"],
            True,
        ),
    ),
)
def test_artist_credit_matching_ignores_case_accents_and_punctuation(
    artist_credit, main_artists, expected
):
    assert musicbrainz._artist_credit_matches(artist_credit, main_artists) is expected


def test_track_candidates_use_only_the_top_score_among_artist_matches(mocker):
    mocker.patch.object(
        musicbrainz,
        "search_release_group",
        return_value={
            "release-groups": [
                _release_group("Test Song", "Unrelated Artist", "1990-01-01", score=100),
                _release_group("Test Song", "Test Artist", "2020-01-01", score=100),
                _release_group("Test Song", "Test Artist", "2000-01-01", score=90),
            ]
        },
    )

    candidates = musicbrainz._track_candidates("Test Song", ["Test Artist"])

    assert [candidate["date"] for candidate in candidates] == ["2020-01-01"]


def test_track_candidates_keep_every_group_at_the_top_score(mocker):
    mocker.patch.object(
        musicbrainz,
        "search_release_group",
        return_value={
            "release-groups": [
                _release_group("Test Song", "Test Artist", "2000-01-01", score=100),
                _release_group("Test Song", "Test Artist", "2001-01-01", score=100),
            ]
        },
    )

    candidates = musicbrainz._track_candidates("Test Song", ["Test Artist"])

    assert [candidate["date"] for candidate in candidates] == ["2000-01-01", "2001-01-01"]


def test_track_candidates_skip_a_top_scored_remix(mocker):
    mocker.patch.object(
        musicbrainz,
        "search_release_group",
        return_value={
            "release-groups": [
                _release_group(
                    "Test Song",
                    "Test Artist",
                    "2020-01-01",
                    score=100,
                    secondary_types=["Remix"],
                ),
                _release_group("Test Song", "Test Artist", "2000-01-01", score=90),
            ]
        },
    )

    candidates = musicbrainz._track_candidates("Test Song", ["Test Artist"])

    assert [candidate["date"] for candidate in candidates] == ["2000-01-01"]


def test_album_candidates_use_the_release_date_not_the_group_first_release_date(mocker):
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={
            "recordings": [
                _recording(
                    releases=[
                        _release(
                            date="2011-09-27",
                            group_first_release_date="2010-01-01",
                        )
                    ]
                )
            ]
        },
    )

    candidates = musicbrainz._album_candidates("Test Song", ["Test Artist"])

    assert [candidate["date"] for candidate in candidates] == ["2011-09-27"]


def test_album_candidates_use_the_earliest_release_date_for_one_group(mocker):
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={
            "recordings": [
                _recording(
                    releases=[
                        _release(date="2012-05-01", release_group_id="same-group"),
                        _release(date="2011-09-27", release_group_id="same-group"),
                    ]
                )
            ]
        },
    )

    candidates = musicbrainz._album_candidates("Test Song", ["Test Artist"])

    assert [candidate["date"] for candidate in candidates] == ["2011-09-27"]


def test_album_candidates_skip_recordings_below_the_score_floor(mocker):
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={"recordings": [_recording(score=89, releases=[_release()])]},
    )

    assert musicbrainz._album_candidates("Test Song", ["Test Artist"]) == []


@pytest.mark.parametrize(
    "disambiguation",
    ("live version", "remix", "acoustic", "instrumental", "karaoke", "demo", "radio edit"),
)
def test_album_candidates_skip_non_studio_recordings(mocker, disambiguation):
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={
            "recordings": [
                _recording(disambiguation=disambiguation, releases=[_release()])
            ]
        },
    )

    assert musicbrainz._album_candidates("Test Song", ["Test Artist"]) == []


def test_album_candidates_require_the_recording_title_to_match(mocker):
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={
            "recordings": [
                _recording(title="Test Song Too Hard", releases=[_release()])
            ]
        },
    )

    assert musicbrainz._album_candidates("Test Song", ["Test Artist"]) == []


def test_album_candidates_keep_version_suffixes_after_the_title(mocker):
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={
            "recordings": [
                _recording(title="Test Song (Radio Edit)", releases=[_release()])
            ]
        },
    )

    assert len(musicbrainz._album_candidates("Test Song", ["Test Artist"])) == 1


@pytest.mark.parametrize("primary_type", ("Album", "EP"))
def test_album_candidates_keep_album_and_ep_release_groups(mocker, primary_type):
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={
            "recordings": [_recording(releases=[_release(primary_type=primary_type)])]
        },
    )

    assert len(musicbrainz._album_candidates("Test Song", ["Test Artist"])) == 1


def test_album_candidates_skip_single_release_groups(mocker):
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={
            "recordings": [_recording(releases=[_release(primary_type="Single")])]
        },
    )

    assert musicbrainz._album_candidates("Test Song", ["Test Artist"]) == []


def test_album_candidates_skip_non_official_releases(mocker):
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={"recordings": [_recording(releases=[_release(status="Bootleg")])]},
    )

    assert musicbrainz._album_candidates("Test Song", ["Test Artist"]) == []


@pytest.mark.parametrize(
    "secondary_type",
    ("Compilation", "Live", "Remix", "DJ-mix", "Mixtape/Street", "Demo"),
)
def test_album_candidates_skip_non_original_release_groups(mocker, secondary_type):
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={
            "recordings": [
                _recording(releases=[_release(secondary_types=[secondary_type])])
            ]
        },
    )

    assert musicbrainz._album_candidates("Test Song", ["Test Artist"]) == []


def test_album_candidates_keep_a_soundtrack_release_group(mocker):
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={
            "recordings": [_recording(releases=[_release(secondary_types=["Soundtrack"])])]
        },
    )

    assert len(musicbrainz._album_candidates("Test Song", ["Test Artist"])) == 1


def test_album_candidates_keep_at_most_five_release_groups(mocker):
    releases = [
        _release(
            date=f"200{index}-01-01",
            release_group_id=f"group-{index}",
            release_group_title=f"Album {index}",
        )
        for index in range(6)
    ]
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={"recordings": [_recording(releases=releases)]},
    )

    candidates = musicbrainz._album_candidates("Test Song", ["Test Artist"])

    assert [candidate["date"] for candidate in candidates] == [
        "2000-01-01",
        "2001-01-01",
        "2002-01-01",
        "2003-01-01",
        "2004-01-01",
    ]


def test_album_candidates_skip_recordings_by_a_different_artist(mocker):
    mocker.patch.object(
        musicbrainz,
        "search_recordings",
        return_value={
            "recordings": [
                _recording(artist="Other Artist", releases=[_release()])
            ]
        },
    )

    assert musicbrainz._album_candidates("Test Song", ["Test Artist"]) == []


def test_the_musicbrainz_stage_passes_the_main_artist_list_end_to_end(mocker):
    search_or_raise = mocker.patch.object(musicbrainz, "search_or_raise", return_value=[])

    service.run_structured_source_stage(
        service.MUSICBRAINZ_SOURCE,
        "Test Song (feat. Guest)",
        ["Test Artist", "Second Artist"],
    )

    search_or_raise.assert_called_once_with("Test Song", ["Test Artist", "Second Artist"])


@pytest.mark.parametrize(
    "source_name,source_module",
    (
        (service.DISCOGS_SOURCE, discogs),
        (service.WIKIDATA_SOURCE, wikidata),
    ),
)
def test_other_structured_sources_receive_the_joined_artist_string(
    source_name, source_module, mocker
):
    search = mocker.patch.object(source_module, "search", return_value=[])

    service.run_structured_source_stage(source_name, "Test Song", ["Test Artist", "Second Artist"])

    search.assert_called_once_with("Test Song", "Test Artist & Second Artist")


@respx.mock
def test_search_returns_empty_list_on_request_failure(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get(url__regex=r"musicbrainz\.org/ws/2/release-group/").mock(
        return_value=httpx.Response(500)
    )

    assert musicbrainz.search("Test Song", ["Test Artist"]) == []


def test_a_failed_call_holds_every_caller_for_the_backed_off_delay(mocker):
    mocker.patch("app.metadata.sources.pacing.time.monotonic", return_value=100.0)
    sleep = mocker.patch("app.metadata.sources.pacing.time.sleep")
    limiter = musicbrainz._AdaptiveRateLimiter()

    limiter.record_failure()
    limiter.wait()

    (slept_seconds,), unused_keywords = sleep.call_args
    assert slept_seconds == pytest.approx(limiter.delay_seconds)
