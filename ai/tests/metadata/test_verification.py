"""Unit tests for the story 18 lock-evaluation logic.

Tests cover:
- Exact 3-way agreement locks with no LLM call
- Every short-of-lock combination (partial agreement, missing source,
  3-way disagreement) routes to Wikipedia + reconciliation
- A genuine no-answer (all sources empty) routes to MANUAL_REVIEW
- A locked song's verification_status is VERIFIED
- An LLM-reconciled song's verification_status is NEEDS_REVIEW
- A no-answer song's verification_status is MANUAL_ENTRY
"""

import json
from pathlib import Path

import httpx
import pytest
import respx

from app.metadata.sources import discogs, musicbrainz, wikidata
from app.metadata.verification import (
    VerificationRoute,
    VerificationStatus,
    _all_three_agree,
    _extract_earliest_year,
    evaluate_lock,
    route_to_verification_status,
)


def _mb_candidate(year: int) -> dict:
    return {"query": "track", "title": "Test", "artist": "Artist", "date": str(year), "type": "Single", "score": 100}


def _discogs_candidate(year: int) -> dict:
    return {"query": "track", "title": "Test", "year": year}


def _wikidata_candidate(year: int) -> dict:
    return {"query": "track", "description": "single by Artist", "date": f"{year}-01-01"}


class TestExtractEarliestYear:
    def test_picks_the_minimum_across_multiple_candidates(self):
        candidates = [_mb_candidate(2000), _mb_candidate(1998), _mb_candidate(2005)]
        assert _extract_earliest_year(candidates) == 1998

    def test_handles_a_year_field_directly(self):
        assert _extract_earliest_year([_discogs_candidate(1995)]) == 1995

    def test_handles_a_date_field_with_full_iso_string(self):
        assert _extract_earliest_year([{"date": "2003-07-15"}]) == 2003

    def test_handles_wikidata_zero_padded_unknown_month(self):
        assert _extract_earliest_year([{"date": "1999-00-00"}]) == 1999

    def test_returns_none_for_an_empty_list(self):
        assert _extract_earliest_year([]) is None

    def test_returns_none_when_all_candidates_have_no_year(self):
        assert _extract_earliest_year([{"title": "Test"}]) is None


class TestAllThreeAgree:
    def test_returns_true_when_all_three_years_match(self):
        assert _all_three_agree(1999, 1999, 1999) is True

    def test_returns_false_when_two_agree_and_one_differs(self):
        assert _all_three_agree(1999, 1999, 2000) is False

    def test_returns_false_when_one_source_is_missing(self):
        assert _all_three_agree(1999, None, 1999) is False

    def test_returns_false_when_all_three_disagree(self):
        assert _all_three_agree(1998, 1999, 2000) is False

    def test_returns_false_when_all_are_none(self):
        assert _all_three_agree(None, None, None) is False


class TestEvaluateLock:
    def test_exact_agreement_locks_with_no_llm_call(self, mocker):
        wikipedia_mock = mocker.patch("app.metadata.verification._run_wikipedia_extraction")
        reconciliation_mock = mocker.patch("app.metadata.verification._run_four_source_reconciliation")

        release_year, confidence, route = evaluate_lock(
            "Test Song", "Test Artist",
            [_mb_candidate(1999)],
            [_discogs_candidate(1999)],
            [_wikidata_candidate(1999)],
            [],
        )

        assert route == VerificationRoute.LOCKED
        assert release_year == 1999
        assert confidence == "high"
        wikipedia_mock.assert_not_called()
        reconciliation_mock.assert_not_called()

    def test_partial_agreement_locks_when_wikipedia_makes_three_sources_agree(self, mocker):
        extraction_result = mocker.MagicMock()
        extraction_result.release_year = 1998
        extraction_result.confidence = "medium"
        wikipedia_mock = mocker.patch(
            "app.metadata.verification._run_wikipedia_extraction", return_value=extraction_result
        )
        reconciliation_result = mocker.MagicMock()
        reconciliation_result.release_year = 1998
        reconciliation_result.confidence = "medium"
        mocker.patch(
            "app.metadata.verification._run_four_source_reconciliation", return_value=reconciliation_result
        )

        release_year, confidence, route = evaluate_lock(
            "Test Song", "Test Artist",
            [_mb_candidate(1998)],
            [_discogs_candidate(1999)],
            [_wikidata_candidate(1998)],
            [{"query": "track", "page_title": "Test Song", "extract": "Released in 1998."}],
        )

        assert route == VerificationRoute.LOCKED_WITH_WIKIPEDIA
        assert release_year == 1998
        assert confidence == "high"
        wikipedia_mock.assert_called_once()

    def test_missing_source_locks_when_wikipedia_makes_three_sources_agree(self, mocker):
        extraction_result = mocker.MagicMock()
        extraction_result.release_year = 2001
        extraction_result.confidence = "high"
        mocker.patch(
            "app.metadata.verification._run_wikipedia_extraction", return_value=extraction_result
        )
        reconciliation_result = mocker.MagicMock()
        reconciliation_result.release_year = 2001
        reconciliation_result.confidence = "high"
        mocker.patch(
            "app.metadata.verification._run_four_source_reconciliation", return_value=reconciliation_result
        )

        _release_year, _confidence, route = evaluate_lock(
            "Test Song", "Test Artist",
            [_mb_candidate(2001)],
            [],
            [_wikidata_candidate(2001)],
            [],
        )

        assert route == VerificationRoute.LOCKED_WITH_WIKIPEDIA

    def test_all_sources_empty_routes_to_manual_review(self, mocker):
        mocker.patch(
            "app.metadata.verification._run_wikipedia_extraction", return_value=None
        )
        mocker.patch(
            "app.metadata.verification._run_four_source_reconciliation", return_value=None
        )

        release_year, confidence, route = evaluate_lock(
            "Obscure Song", "Unknown Artist",
            [], [], [],
            [],
        )

        assert route == VerificationRoute.MANUAL_REVIEW
        assert release_year is None

    def test_reconciliation_returning_none_year_routes_to_manual_review(self, mocker):
        mocker.patch(
            "app.metadata.verification._run_wikipedia_extraction", return_value=None
        )
        reconciliation_result = mocker.MagicMock()
        reconciliation_result.release_year = None
        mocker.patch(
            "app.metadata.verification._run_four_source_reconciliation", return_value=reconciliation_result
        )

        _release_year, _confidence, route = evaluate_lock(
            "Test Song", "Test Artist",
            [_mb_candidate(1999)],
            [],
            [],
            [],
        )

        assert route == VerificationRoute.MANUAL_REVIEW

    def test_wikipedia_extraction_failure_still_attempts_reconciliation(self, mocker):
        mocker.patch(
            "app.metadata.verification._run_wikipedia_extraction", return_value=None
        )
        reconciliation_result = mocker.MagicMock()
        reconciliation_result.release_year = 1997
        reconciliation_result.confidence = "medium"
        mocker.patch(
            "app.metadata.verification._run_four_source_reconciliation", return_value=reconciliation_result
        )

        release_year, _confidence, route = evaluate_lock(
            "Test Song", "Test Artist",
            [_mb_candidate(1997)],
            [_discogs_candidate(1998)],
            [],
            [],
        )

        assert route == VerificationRoute.LLM_RECONCILED
        assert release_year == 1997

    def test_three_of_four_agreement_locks_without_reconciliation(self, mocker):
        wikipedia_result = mocker.MagicMock(release_year=2011, confidence="high")
        mocker.patch("app.metadata.verification._run_wikipedia_extraction", return_value=wikipedia_result)
        reconciliation_mock = mocker.patch("app.metadata.verification._run_four_source_reconciliation")

        release_year, confidence, route = evaluate_lock(
            "Test Song", "Test Artist",
            [_mb_candidate(2011)],
            [_discogs_candidate(2011)],
            [_wikidata_candidate(2010)],
            [{"extract": "Test Song was released in 2011."}],
        )

        assert route == VerificationRoute.LOCKED_WITH_WIKIPEDIA
        assert release_year == 2011
        assert confidence == "high"
        reconciliation_mock.assert_not_called()

    def test_two_of_four_agreement_still_reconciles(self, mocker):
        wikipedia_result = mocker.MagicMock(release_year=2012, confidence="medium")
        mocker.patch("app.metadata.verification._run_wikipedia_extraction", return_value=wikipedia_result)
        reconciliation_result = mocker.MagicMock(release_year=2011, confidence="medium")
        reconciliation_mock = mocker.patch(
            "app.metadata.verification._run_four_source_reconciliation", return_value=reconciliation_result
        )

        _release_year, _confidence, route = evaluate_lock(
            "Test Song", "Test Artist",
            [_mb_candidate(2011)],
            [_discogs_candidate(2011)],
            [_wikidata_candidate(2009)],
            [{"extract": "Test Song was released in 2012."}],
        )

        assert route == VerificationRoute.LLM_RECONCILED
        reconciliation_mock.assert_called_once()

    @pytest.mark.parametrize(
        ("candidate_years", "expected_year"),
        [
            ([2009, 2010, 2010], 2010),
            ([2002, 2002, 2003, 2003], 2002),
            ([2009, 2009, 2010], 2009),
        ],
    )
    def test_tight_year_clusters_require_two_sources_for_earliest_year(self, mocker, candidate_years, expected_year):
        musicbrainz_year, discogs_year, wikidata_year, *wikipedia_years = candidate_years
        wikipedia_year = wikipedia_years[0] if wikipedia_years else None
        wikipedia_result = mocker.MagicMock(release_year=wikipedia_year, confidence="high")
        mocker.patch("app.metadata.verification._run_wikipedia_extraction", return_value=wikipedia_result)
        reconciliation_mock = mocker.patch("app.metadata.verification._run_four_source_reconciliation")

        release_year, confidence, route = evaluate_lock(
            "Test Song", "Test Artist",
            [_mb_candidate(musicbrainz_year)],
            [_discogs_candidate(discogs_year)],
            [_wikidata_candidate(wikidata_year)],
            [{"extract": "Test Song release information."}],
        )

        assert route == VerificationRoute.LOCKED_WITH_WIKIPEDIA
        assert release_year == expected_year
        assert confidence == "high"
        reconciliation_mock.assert_not_called()

    def test_outlier_prevents_tight_year_cluster_lock(self, mocker):
        wikipedia_result = mocker.MagicMock(release_year=None, confidence="low")
        mocker.patch("app.metadata.verification._run_wikipedia_extraction", return_value=wikipedia_result)
        reconciliation_result = mocker.MagicMock(release_year=2010, confidence="medium")
        reconciliation_mock = mocker.patch(
            "app.metadata.verification._run_four_source_reconciliation", return_value=reconciliation_result
        )

        _release_year, _confidence, route = evaluate_lock(
            "Test Song", "Test Artist",
            [_mb_candidate(2009)],
            [_discogs_candidate(2010)],
            [_wikidata_candidate(2020)],
            [{"extract": "Test Song release information."}],
        )

        assert route == VerificationRoute.LLM_RECONCILED
        reconciliation_mock.assert_called_once()


class TestRouteToVerificationStatus:
    def test_locked_maps_to_verified(self):
        assert route_to_verification_status(VerificationRoute.LOCKED) == VerificationStatus.VERIFIED

    def test_wikipedia_assisted_lock_maps_to_verified(self):
        assert route_to_verification_status(VerificationRoute.LOCKED_WITH_WIKIPEDIA) == VerificationStatus.VERIFIED

    def test_llm_reconciled_maps_to_needs_review(self):
        assert route_to_verification_status(VerificationRoute.LLM_RECONCILED) == VerificationStatus.NEEDS_REVIEW

    def test_manual_review_maps_to_manual_entry(self):
        assert route_to_verification_status(VerificationRoute.MANUAL_REVIEW) == VerificationStatus.MANUAL_ENTRY

GOLDEN_FIXTURES_DIR = Path(__file__).parent / "fixtures" / "golden"


def _load_golden_fixtures() -> list[dict]:
    return [
        json.loads(fixture_path.read_text(encoding="utf-8"))
        for fixture_path in sorted(GOLDEN_FIXTURES_DIR.glob("*.json"))
    ]


class TestOneYearClusterRefinement:
    def test_single_source_earliest_year_does_not_pull_cluster_down(self):
        from app.metadata.verification import _find_wikipedia_assisted_lock_year

        assert _find_wikipedia_assisted_lock_year([2015, 2016, 2016]) == 2016
        assert _find_wikipedia_assisted_lock_year([2018, 2019, 2019, 2019]) == 2019
        assert _find_wikipedia_assisted_lock_year([2003, 2004, 2004, 2004]) == 2004

    def test_two_sources_support_earliest_year_in_cluster(self):
        from app.metadata.verification import _find_wikipedia_assisted_lock_year

        assert _find_wikipedia_assisted_lock_year([2015, 2015, 2016]) == 2015
        assert _find_wikipedia_assisted_lock_year([2015, 2015, 2016, 2016]) == 2015

    def test_exact_majority_rule_unaffected_by_outlier(self):
        from app.metadata.verification import _find_wikipedia_assisted_lock_year

        assert _find_wikipedia_assisted_lock_year([2016, 2016, 2016]) == 2016
        assert _find_wikipedia_assisted_lock_year([2016, 2016, 2016, 2014]) == 2016


@pytest.mark.parametrize(
    "song_fixture",
    _load_golden_fixtures(),
    ids=lambda fixture: fixture["id"],
)
@respx.mock
def test_golden_lock_evaluation_for_catalog_songs(mocker, song_fixture):
    mocker.patch("app.metadata.sources.pacing.time.sleep")

    def mb_respond(request: httpx.Request) -> httpx.Response:
        if "release-group" in request.url.path:
            return httpx.Response(200, json=song_fixture["musicbrainz"]["release_group"])
        return httpx.Response(200, json=song_fixture["musicbrainz"]["recording"])

    def discogs_respond(request: httpx.Request) -> httpx.Response:
        if "database/search" in request.url.path:
            return httpx.Response(200, json=song_fixture["discogs"]["releases"])
        master_id = request.url.path.rstrip("/").split("/")[-1]
        master = song_fixture["discogs"]["masters"].get(master_id, {})
        return httpx.Response(200, json=master)

    def wikidata_respond(request: httpx.Request) -> httpx.Response:
        action = request.url.params.get("action")
        if action == "wbsearchentities":
            return httpx.Response(200, json=song_fixture["wikidata"]["search"])
        if action == "wbgetentities":
            ids = request.url.params.get("ids", "").split("|")
            entities = {
                eid: song_fixture["wikidata"]["entities"][eid]
                for eid in ids
                if eid in song_fixture["wikidata"]["entities"]
            }
            return httpx.Response(200, json={"entities": entities})
        return httpx.Response(200, json={})

    respx.get(url__regex=r"musicbrainz\.org/ws/2/(release-group|recording)/").mock(side_effect=mb_respond)
    respx.get(url__regex=r"api\.discogs\.com/").mock(side_effect=discogs_respond)
    respx.get(url__regex=r"wikidata\.org/w/api\.php").mock(side_effect=wikidata_respond)

    musicbrainz_candidates = musicbrainz.search(song_fixture["title"], song_fixture["main_artists"])
    discogs_candidates = discogs.search(song_fixture["title"], song_fixture["main_artists"])
    wikidata_candidates = wikidata.search(song_fixture["title"], song_fixture["main_artists"])

    extraction_result = mocker.MagicMock()
    extraction_result.release_year = song_fixture.get("wikipedia_extracted_year")
    extraction_result.confidence = "high"
    mocker.patch(
        "app.metadata.verification._run_wikipedia_extraction", return_value=extraction_result
    )
    reconciliation_mock = mocker.patch("app.metadata.verification._run_four_source_reconciliation")

    release_year, confidence, route = evaluate_lock(
        song_fixture["title"],
        " & ".join(song_fixture["main_artists"]),
        musicbrainz_candidates,
        discogs_candidates,
        wikidata_candidates,
        [{"extract": f"{song_fixture['title']} was released in {song_fixture['expected_year']}."}],
    )

    assert release_year == song_fixture["expected_year"]
    assert confidence == "high"
    assert route in {VerificationRoute.LOCKED, VerificationRoute.LOCKED_WITH_WIKIPEDIA}
    reconciliation_mock.assert_not_called()
