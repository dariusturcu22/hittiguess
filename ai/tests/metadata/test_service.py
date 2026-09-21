import time

import pytest

from app.dedup.schemas import VerifiedSongMatch
from app.metadata import service
from app.metadata.content_safety import ContentSafetyOutcome
from app.metadata.schemas import RejectionReason, SubmissionPreCheckResult
from app.metadata.verification import VerificationRoute


def _youtube_data(**overrides):
    data = {
        "video_title": "Test Song (Official Video)",
        "channel_title": "Test Artist",
        "upload_year": 2020,
        "description": "",
        "category_id": "10",
        "duration_seconds": 210,
    }
    data.update(overrides)
    return data


def _precheck_result(**overrides):
    fields = {
        "title": "Test Song",
        "artist": "Test Artist",
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


def _verified_match():
    return VerifiedSongMatch(
        id=42,
        artist="Test Artist",
        title="Test Song",
        release_year=1999,
        color="8B5CF6",
        confidence="high",
        cosine_distance=0.01,
    )


def _locked_verification_result():
    """Simulates evaluate_lock returning a locked result: all three sources agreed."""
    return 1999, "high", VerificationRoute.LOCKED


def _patch_pipeline_dependencies(mocker, duplicate_match, embedding=None):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "generate_embedding", return_value=embedding or [0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=duplicate_match)
    mocker.patch.object(service, "_run_precheck", return_value=_precheck_result())
    mocker.patch.object(service.musicbrainz, "search", return_value=[])
    mocker.patch.object(service.discogs, "search", return_value=[])
    mocker.patch.object(service.wikidata, "search", return_value=[])
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    return mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())


def test_high_confidence_match_reuses_existing_data_without_running_any_llm(mocker):
    precheck_mock = mocker.patch.object(service, "_run_precheck")
    _patch_pipeline_dependencies(mocker, duplicate_match=_verified_match())

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content.title == "Test Song"
    assert result.content.source == service.DUPLICATE_MATCH_SOURCE_LABEL
    precheck_mock.assert_not_called()


def test_no_match_proceeds_to_the_full_pipeline(mocker):
    _patch_pipeline_dependencies(mocker, duplicate_match=None)

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content.title == "Test Song"
    assert result.content.release_year == 1999
    assert result.content.source == service.LOCKED_SOURCE_LABEL
    assert result.content.color == "8B5CF6"


def test_low_confidence_match_proceeds_to_the_full_pipeline(mocker):
    low_confidence_match = _verified_match()
    low_confidence_match.cosine_distance = service.HIGH_CONFIDENCE_COSINE_DISTANCE_THRESHOLD + 0.5

    _patch_pipeline_dependencies(mocker, duplicate_match=low_confidence_match)

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content.source == service.LOCKED_SOURCE_LABEL


def test_duplicate_check_failure_falls_back_to_the_full_pipeline(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "generate_embedding", side_effect=RuntimeError("embedding API down"))
    mocker.patch.object(service, "_run_precheck", return_value=_precheck_result())
    mocker.patch.object(service.musicbrainz, "search", return_value=[])
    mocker.patch.object(service.discogs, "search", return_value=[])
    mocker.patch.object(service.wikidata, "search", return_value=[])
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content.source == service.LOCKED_SOURCE_LABEL


def test_resolve_metadata_returns_error_status_when_the_full_pipeline_raises(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", side_effect=RuntimeError("YouTube API down"))

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "ERROR"
    assert result.content is None


def test_a_hard_filtered_submission_never_calls_any_llm(mocker):
    """Neither the duplicate check nor the precheck call ever runs for a
    submission that fails the deterministic category/duration filter."""
    mocker.patch.object(
        service.youtube,
        "fetch_youtube_metadata",
        return_value=_youtube_data(category_id="22", duration_seconds=3600),
    )
    embedding_mock = mocker.patch.object(service, "generate_embedding")
    precheck_mock = mocker.patch.object(service, "_run_precheck")

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == service.REJECTED_STATUS
    assert result.rejection_reason == RejectionReason.NOT_MUSIC
    embedding_mock.assert_not_called()
    precheck_mock.assert_not_called()


PER_SOURCE_DELAY_SECONDS = 0.3
STRUCTURED_SOURCE_COUNT = 3
SEQUENTIAL_TOTAL_SECONDS = PER_SOURCE_DELAY_SECONDS * STRUCTURED_SOURCE_COUNT
CONCURRENCY_ELAPSED_CEILING_SECONDS = SEQUENTIAL_TOTAL_SECONDS / 2

MATCHING_RELEASE_YEAR = "1999"
DISAGREEING_RELEASE_YEAR = "2001"


def _delayed_search(delay_seconds):
    def search(title, artist):
        time.sleep(delay_seconds)
        return []

    return search


def _candidate_for_year(release_year):
    return {"query": "track", "title": "Test Song", "artist": "Test Artist", "date": release_year, "type": "Single", "score": 100}


def test_structured_sources_are_fetched_concurrently_not_sequentially(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service, "_run_precheck", return_value=_precheck_result())
    for structured_source in (service.musicbrainz, service.discogs, service.wikidata):
        mocker.patch.object(structured_source, "search", side_effect=_delayed_search(PER_SOURCE_DELAY_SECONDS))
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())

    start_time = time.perf_counter()
    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")
    elapsed_seconds = time.perf_counter() - start_time

    assert result.status == "SUCCESS"
    assert elapsed_seconds < CONCURRENCY_ELAPSED_CEILING_SECONDS


def test_one_structured_source_raising_does_not_prevent_the_others_from_being_used(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service, "_run_precheck", return_value=_precheck_result())

    musicbrainz_candidates = [_candidate_for_year(MATCHING_RELEASE_YEAR)]
    mocker.patch.object(service.musicbrainz, "search", return_value=musicbrainz_candidates)
    mocker.patch.object(service.discogs, "search", side_effect=RuntimeError("Discogs API down"))
    mocker.patch.object(service.wikidata, "search", return_value=[])
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"


def test_three_structured_sources_are_gathered_concurrently(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service, "_run_precheck", return_value=_precheck_result())
    structured_source_mocks = {
        "musicbrainz": mocker.patch.object(service.musicbrainz, "search", return_value=[]),
        "discogs": mocker.patch.object(service.discogs, "search", return_value=[]),
        "wikidata": mocker.patch.object(service.wikidata, "search", return_value=[]),
    }
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    for source_mock in structured_source_mocks.values():
        source_mock.assert_called_once()


def test_wikipedia_is_not_fetched_when_the_structured_sources_all_agree(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service, "_run_precheck", return_value=_precheck_result())

    agreeing_candidates = [_candidate_for_year(MATCHING_RELEASE_YEAR)]
    mocker.patch.object(service.musicbrainz, "search", return_value=agreeing_candidates)
    mocker.patch.object(service.discogs, "search", return_value=agreeing_candidates)
    mocker.patch.object(service.wikidata, "search", return_value=agreeing_candidates)
    wikipedia_mock = mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    wikipedia_mock.assert_not_called()


def test_wikipedia_is_fetched_after_the_gather_when_the_structured_sources_disagree(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service, "_run_precheck", return_value=_precheck_result())

    mocker.patch.object(service.musicbrainz, "search", return_value=[_candidate_for_year(MATCHING_RELEASE_YEAR)])
    mocker.patch.object(service.discogs, "search", return_value=[_candidate_for_year(DISAGREEING_RELEASE_YEAR)])
    mocker.patch.object(service.wikidata, "search", return_value=[])
    wikipedia_mock = mocker.patch.object(service.wikipedia, "search", return_value=[])
    reconciled_result = (2000, "medium", VerificationRoute.LLM_RECONCILED)
    mocker.patch("app.metadata.service.evaluate_lock", return_value=reconciled_result)

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content.source == service.RECONCILED_SOURCE_LABEL
    wikipedia_mock.assert_called_once()


def _patch_gated_pipeline(mocker, precheck):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service, "_run_precheck", return_value=precheck)
    return mocker.patch.object(service, "_run_verification_pipeline", return_value=None)


def test_injection_flagged_submission_is_rejected_and_never_reaches_the_pipeline(mocker):
    pipeline_mock = _patch_gated_pipeline(
        mocker, _precheck_result(contains_injection_attempt=True, injection_reasoning="Told the model to ignore rules.")
    )

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == service.REJECTED_STATUS
    assert result.rejection_reason == RejectionReason.PROMPT_INJECTION
    assert result.content is None
    pipeline_mock.assert_not_called()


def test_non_music_submission_is_rejected_and_never_reaches_the_pipeline(mocker):
    pipeline_mock = _patch_gated_pipeline(mocker, _precheck_result(is_song=False))

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == service.REJECTED_STATUS
    assert result.rejection_reason == RejectionReason.NOT_MUSIC
    pipeline_mock.assert_not_called()


def test_compilation_submission_is_rejected_and_never_reaches_the_pipeline(mocker):
    pipeline_mock = _patch_gated_pipeline(mocker, _precheck_result(is_compilation=True))

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == service.REJECTED_STATUS
    assert result.rejection_reason == RejectionReason.COMPILATION
    pipeline_mock.assert_not_called()


def test_clean_music_passes_the_gate_and_reaches_the_pipeline(mocker):
    pipeline_mock = _patch_gated_pipeline(mocker, _precheck_result())

    service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    pipeline_mock.assert_called_once()


def test_precheck_falls_back_to_regex_cleaned_names_for_an_unidentifiable_submission(mocker):
    """The precheck call itself still runs (it also carries the safety
    checks), but a null title/artist from a genuinely unidentifiable
    submission falls back to the free regex clean rather than passing
    None into the structured-source queries."""
    mocker.patch.object(
        service.youtube,
        "fetch_youtube_metadata",
        return_value=_youtube_data(video_title="DJ Mixtape Vol 3 track 7", channel_title="randomuploader99"),
    )
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service, "_run_precheck", return_value=_precheck_result(title=None, artist=None))
    musicbrainz_mock = mocker.patch.object(service.musicbrainz, "search", return_value=[])
    mocker.patch.object(service.discogs, "search", return_value=[])
    mocker.patch.object(service.wikidata, "search", return_value=[])
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())

    service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    musicbrainz_mock.assert_called_once_with("DJ Mixtape Vol 3 track 7", "randomuploader99")


def test_verification_pipeline_reports_the_track_entity_sitelinks_count(mocker):
    mocker.patch.object(service.musicbrainz, "search", return_value=[])
    mocker.patch.object(service.discogs, "search", return_value=[])
    mocker.patch.object(
        service.wikidata,
        "search",
        return_value=[{"query": "track", "entity_id": "Q1", "description": "1999 song by Test Artist", "date": 1999}],
    )
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())
    sitelinks_mock = mocker.patch.object(service.wikidata, "get_sitelinks_count", return_value=12)

    result = service._run_verification_pipeline("Test Song", "Test Artist", "8B5CF6")

    sitelinks_mock.assert_called_once_with("Q1")
    assert result.sitelinks_count == 12


def test_verification_pipeline_leaves_sitelinks_count_unknown_without_a_wikidata_match(mocker):
    mocker.patch.object(service.musicbrainz, "search", return_value=[])
    mocker.patch.object(service.discogs, "search", return_value=[])
    mocker.patch.object(service.wikidata, "search", return_value=[])
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())
    sitelinks_mock = mocker.patch.object(service.wikidata, "get_sitelinks_count", return_value=12)

    result = service._run_verification_pipeline("Test Song", "Test Artist", "8B5CF6")

    sitelinks_mock.assert_not_called()
    assert result.sitelinks_count is None


@pytest.mark.parametrize(
    ("display_title", "source_query_title"),
    [
        ("Titanium (feat. Sia)", "Titanium"),
        ("Song Title ft. Featured Artist", "Song Title ft. Featured Artist"),
        ("Song Without Guest", "Song Without Guest"),
    ],
)
def test_structured_queries_strip_only_featured_artist_suffixes(mocker, display_title, source_query_title):
    musicbrainz_mock = mocker.patch.object(service.musicbrainz, "search", return_value=[])
    mocker.patch.object(service.discogs, "search", return_value=[])
    mocker.patch.object(service.wikidata, "search", return_value=[])
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())

    result = service._run_verification_pipeline(display_title, "Test Artist", "8B5CF6")

    musicbrainz_mock.assert_called_once_with(source_query_title, "Test Artist")
    assert result.title == display_title
