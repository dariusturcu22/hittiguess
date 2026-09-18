import time

from app.dedup.schemas import VerifiedSongMatch
from app.metadata import service
from app.metadata.content_safety import ContentSafetyOutcome, PASSED_OUTCOME
from app.metadata.schemas import RejectionReason, SongMetadataResult, TitleArtistExtractionResult
from app.metadata.verification import VerificationRoute


def _youtube_data():
    return {
        "video_title": "Test Song (Official Video)",
        "channel_title": "Test Artist",
        "upload_year": 2020,
        "description": "",
        "category_id": "10",
        "duration_seconds": 210,
    }


def _synthesized_result():
    return SongMetadataResult(
        title="Test Song",
        artist="Test Artist",
        release_year=1999,
        gradient_color1="8B5CF6",
        gradient_color2="EC4899",
        confidence="high",
        source="MusicBrainz",
        reasoning="Matched exactly.",
        verification_status=None,
    )


def _verified_match():
    return VerifiedSongMatch(
        id=42,
        artist="Test Artist",
        title="Test Song",
        release_year=1999,
        gradient_color1="8B5CF6",
        gradient_color2="EC4899",
        confidence="high",
        cosine_distance=0.01,
    )


def _locked_verification_result():
    """Simulates evaluate_lock returning a locked result: all three sources agreed."""
    return 1999, "high", VerificationRoute.LOCKED


def _patch_pipeline_dependencies(mocker, duplicate_match, embedding=None):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "_extract_title_and_artist", return_value=("Test Song", "Test Artist"))
    mocker.patch.object(service, "generate_embedding", return_value=embedding or [0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=duplicate_match)
    mocker.patch.object(service.content_safety, "evaluate", return_value=PASSED_OUTCOME)
    mocker.patch.object(service.musicbrainz, "search", return_value=[])
    mocker.patch.object(service.discogs, "search", return_value=[])
    mocker.patch.object(service.wikidata, "search", return_value=[])
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())
    return mocker.patch.object(service, "synthesize", return_value=_synthesized_result())


def test_high_confidence_match_reuses_existing_data_without_running_the_llm(mocker):
    synthesize_mock = _patch_pipeline_dependencies(mocker, duplicate_match=_verified_match())

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content.title == "Test Song"
    assert result.content.source == service.DUPLICATE_MATCH_SOURCE_LABEL
    synthesize_mock.assert_not_called()


def test_no_match_proceeds_to_the_full_pipeline(mocker):
    synthesize_mock = _patch_pipeline_dependencies(mocker, duplicate_match=None)

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content.title == "Test Song"
    assert result.content.release_year == 1999
    assert result.content.source == service.LOCKED_SOURCE_LABEL
    synthesize_mock.assert_called_once()


def test_low_confidence_match_proceeds_to_the_full_pipeline(mocker):
    low_confidence_match = _verified_match()
    low_confidence_match.cosine_distance = service.HIGH_CONFIDENCE_COSINE_DISTANCE_THRESHOLD + 0.5

    synthesize_mock = _patch_pipeline_dependencies(mocker, duplicate_match=low_confidence_match)

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content.source == service.LOCKED_SOURCE_LABEL
    synthesize_mock.assert_called_once()


def test_duplicate_check_failure_falls_back_to_the_full_pipeline(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "_extract_title_and_artist", return_value=("Test Song", "Test Artist"))
    mocker.patch.object(service, "generate_embedding", side_effect=RuntimeError("embedding API down"))
    mocker.patch.object(service.content_safety, "evaluate", return_value=PASSED_OUTCOME)
    mocker.patch.object(service.musicbrainz, "search", return_value=[])
    mocker.patch.object(service.discogs, "search", return_value=[])
    mocker.patch.object(service.wikidata, "search", return_value=[])
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())
    synthesize_mock = mocker.patch.object(service, "synthesize", return_value=_synthesized_result())

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content.source == service.LOCKED_SOURCE_LABEL
    synthesize_mock.assert_called_once()


def test_resolve_metadata_returns_error_status_when_the_full_pipeline_raises(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", side_effect=RuntimeError("YouTube API down"))

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "ERROR"
    assert result.content is None


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
    mocker.patch.object(service, "_extract_title_and_artist", return_value=("Test Song", "Test Artist"))
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service.content_safety, "evaluate", return_value=PASSED_OUTCOME)
    for structured_source in (service.musicbrainz, service.discogs, service.wikidata):
        mocker.patch.object(structured_source, "search", side_effect=_delayed_search(PER_SOURCE_DELAY_SECONDS))
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())
    mocker.patch.object(service, "synthesize", return_value=_synthesized_result())

    start_time = time.perf_counter()
    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")
    elapsed_seconds = time.perf_counter() - start_time

    assert result.status == "SUCCESS"
    assert elapsed_seconds < CONCURRENCY_ELAPSED_CEILING_SECONDS


def test_one_structured_source_raising_does_not_prevent_the_others_from_being_used(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "_extract_title_and_artist", return_value=("Test Song", "Test Artist"))
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service.content_safety, "evaluate", return_value=PASSED_OUTCOME)

    musicbrainz_candidates = [_candidate_for_year(MATCHING_RELEASE_YEAR)]
    mocker.patch.object(service.musicbrainz, "search", return_value=musicbrainz_candidates)
    mocker.patch.object(service.discogs, "search", side_effect=RuntimeError("Discogs API down"))
    mocker.patch.object(service.wikidata, "search", return_value=[])
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())

    captured_metadata = {}

    def capture_prompt(all_metadata):
        captured_metadata.update(all_metadata)
        return "prompt"

    mocker.patch.object(service.prompt, "build", side_effect=capture_prompt)
    synthesize_mock = mocker.patch.object(service, "synthesize", return_value=_synthesized_result())

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    synthesize_mock.assert_called_once()
    assert captured_metadata["musicbrainz"] == musicbrainz_candidates
    assert captured_metadata["discogs"] == service.EMPTY_SOURCE_RESULT


def test_three_structured_sources_are_gathered_and_passed_to_synthesis(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "_extract_title_and_artist", return_value=("Test Song", "Test Artist"))
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service.content_safety, "evaluate", return_value=PASSED_OUTCOME)
    structured_source_mocks = {
        "musicbrainz": mocker.patch.object(service.musicbrainz, "search", return_value=[]),
        "discogs": mocker.patch.object(service.discogs, "search", return_value=[]),
        "wikidata": mocker.patch.object(service.wikidata, "search", return_value=[]),
    }
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())

    captured_metadata = {}

    def capture_prompt(all_metadata):
        captured_metadata.update(all_metadata)
        return "prompt"

    mocker.patch.object(service.prompt, "build", side_effect=capture_prompt)
    synthesize_mock = mocker.patch.object(service, "synthesize", return_value=_synthesized_result())

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    for source_name, source_mock in structured_source_mocks.items():
        source_mock.assert_called_once()
        assert source_name in captured_metadata
    assert "youtube" in captured_metadata
    synthesize_mock.assert_called_once()


def test_wikipedia_is_not_fetched_when_the_three_structured_sources_lock(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service.content_safety, "evaluate", return_value=PASSED_OUTCOME)

    agreeing_candidates = [_candidate_for_year(MATCHING_RELEASE_YEAR)]
    mocker.patch.object(service.musicbrainz, "search", return_value=agreeing_candidates)
    mocker.patch.object(service.discogs, "search", return_value=agreeing_candidates)
    mocker.patch.object(service.wikidata, "search", return_value=agreeing_candidates)
    wikipedia_mock = mocker.patch.object(service.wikipedia, "search", return_value=[])
    mocker.patch("app.metadata.service.evaluate_lock", return_value=_locked_verification_result())
    mocker.patch.object(service.prompt, "build", return_value="prompt")
    mocker.patch.object(service, "synthesize", return_value=_synthesized_result())

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    wikipedia_mock.assert_not_called()


def test_wikipedia_is_fetched_after_the_gather_when_the_structured_sources_disagree(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service.content_safety, "evaluate", return_value=PASSED_OUTCOME)

    mocker.patch.object(service.musicbrainz, "search", return_value=[_candidate_for_year(MATCHING_RELEASE_YEAR)])
    mocker.patch.object(service.discogs, "search", return_value=[_candidate_for_year(DISAGREEING_RELEASE_YEAR)])
    mocker.patch.object(service.wikidata, "search", return_value=[])
    wikipedia_mock = mocker.patch.object(service.wikipedia, "search", return_value=[])
    reconciled_result = (2000, "medium", VerificationRoute.LLM_RECONCILED)
    mocker.patch("app.metadata.service.evaluate_lock", return_value=reconciled_result)
    mocker.patch.object(service.prompt, "build", return_value="prompt")
    mocker.patch.object(service, "synthesize", return_value=_synthesized_result())

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content.source == service.RECONCILED_SOURCE_LABEL
    wikipedia_mock.assert_called_once()


def _reject(reason):
    return ContentSafetyOutcome(rejected=True, rejection_reason=reason, rejection_detail=f"rejected: {reason.value}")


def _patch_gated_pipeline(mocker, outcome):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "_extract_title_and_artist", return_value=("Test Song", "Test Artist"))
    mocker.patch.object(service, "generate_embedding", return_value=[0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=None)
    mocker.patch.object(service.content_safety, "evaluate", return_value=outcome)
    return mocker.patch.object(service, "_run_verification_pipeline", return_value=_synthesized_result())


def test_injection_flagged_submission_is_rejected_and_never_reaches_the_pipeline(mocker):
    pipeline_mock = _patch_gated_pipeline(mocker, _reject(RejectionReason.PROMPT_INJECTION))

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == service.REJECTED_STATUS
    assert result.rejection_reason == RejectionReason.PROMPT_INJECTION
    assert result.content is None
    pipeline_mock.assert_not_called()


def test_non_music_submission_is_rejected_and_never_reaches_the_pipeline(mocker):
    pipeline_mock = _patch_gated_pipeline(mocker, _reject(RejectionReason.NOT_MUSIC))

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == service.REJECTED_STATUS
    assert result.rejection_reason == RejectionReason.NOT_MUSIC
    pipeline_mock.assert_not_called()


def test_compilation_submission_is_rejected_and_never_reaches_the_pipeline(mocker):
    pipeline_mock = _patch_gated_pipeline(mocker, _reject(RejectionReason.COMPILATION))

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == service.REJECTED_STATUS
    assert result.rejection_reason == RejectionReason.COMPILATION
    pipeline_mock.assert_not_called()


def test_clean_music_passes_the_gate_and_reaches_the_pipeline(mocker):
    pipeline_mock = _patch_gated_pipeline(mocker, PASSED_OUTCOME)

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    pipeline_mock.assert_called_once()


def test_extract_title_and_artist_prefers_the_llm_split_over_the_raw_regex_clean(mocker):
    """The regression case: a raw "Artist - Title (tags)" YouTube title breaks an
    exact-match structured-source query when the artist prefix isn't split off.
    The LLM extraction step must be the one used, not the regex-only fallback."""
    mocker.patch.object(
        service,
        "extract_structured",
        return_value=TitleArtistExtractionResult(
            title="Never Gonna Give You Up", artist="Rick Astley", confidence="high", reasoning="Well-known song."
        ),
    )

    title, artist = service._extract_title_and_artist(
        {"video_title": "Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)", "channel_title": "Rick Astley"}
    )

    assert title == "Never Gonna Give You Up"
    assert artist == "Rick Astley"


def test_extract_title_and_artist_falls_back_to_regex_cleaning_on_extraction_failure(mocker):
    mocker.patch.object(service, "extract_structured", side_effect=RuntimeError("DeepInfra API down"))

    title, artist = service._extract_title_and_artist(
        {"video_title": "Test Song (Official Video)", "channel_title": "Test Artist"}
    )

    assert title == "Test Song"
    assert artist == "Test Artist"


def test_extract_title_and_artist_falls_back_to_regex_cleaning_for_an_unidentifiable_submission(mocker):
    mocker.patch.object(
        service,
        "extract_structured",
        return_value=TitleArtistExtractionResult(title=None, artist=None, confidence="low", reasoning="Not identifiable."),
    )

    title, artist = service._extract_title_and_artist(
        {"video_title": "DJ Mixtape Vol 3 track 7", "channel_title": "randomuploader99"}
    )

    assert title == "DJ Mixtape Vol 3 track 7"
    assert artist == "randomuploader99"
