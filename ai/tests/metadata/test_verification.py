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

import pytest

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

    def test_partial_agreement_routes_to_wikipedia_and_reconciliation(self, mocker):
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

        assert route == VerificationRoute.LLM_RECONCILED
        assert release_year == 1998
        wikipedia_mock.assert_called_once()

    def test_missing_source_routes_to_wikipedia_and_reconciliation(self, mocker):
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

        assert route == VerificationRoute.LLM_RECONCILED

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


class TestRouteToVerificationStatus:
    def test_locked_maps_to_verified(self):
        assert route_to_verification_status(VerificationRoute.LOCKED) == VerificationStatus.VERIFIED

    def test_llm_reconciled_maps_to_needs_review(self):
        assert route_to_verification_status(VerificationRoute.LLM_RECONCILED) == VerificationStatus.NEEDS_REVIEW

    def test_manual_review_maps_to_manual_entry(self):
        assert route_to_verification_status(VerificationRoute.MANUAL_REVIEW) == VerificationStatus.MANUAL_ENTRY
