from app.dedup.schemas import VerifiedSongMatch
from app.metadata import service
from app.metadata.content_safety import ContentSafetyOutcome, PASSED_OUTCOME
from app.metadata.schemas import RejectionReason, SongMetadataResult
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


def _reject(reason):
    return ContentSafetyOutcome(rejected=True, rejection_reason=reason, rejection_detail=f"rejected: {reason.value}")


def _patch_gated_pipeline(mocker, outcome):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
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
