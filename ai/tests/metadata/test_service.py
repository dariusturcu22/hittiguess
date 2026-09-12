from app.dedup.schemas import VerifiedSongMatch
from app.metadata import service
from app.metadata.schemas import SongMetadataResult


def _youtube_data():
    return {
        "video_title": "Test Song (Official Video)",
        "channel_title": "Test Artist",
        "upload_year": 2020,
        "description": "",
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


def _patch_pipeline_dependencies(mocker, duplicate_match, embedding=None):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "generate_embedding", return_value=embedding or [0.1, 0.2, 0.3])
    mocker.patch.object(service, "find_best_verified_match", return_value=duplicate_match)
    mocker.patch.object(service.musicbrainz, "search", return_value=[])
    mocker.patch.object(service.wikidata, "search", return_value=[])
    mocker.patch.object(service.wikipedia, "search", return_value=[])
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
    assert result.content == _synthesized_result()
    synthesize_mock.assert_called_once()


def test_low_confidence_match_proceeds_to_the_full_pipeline(mocker):
    low_confidence_match = _verified_match()
    low_confidence_match.cosine_distance = service.HIGH_CONFIDENCE_COSINE_DISTANCE_THRESHOLD + 0.5

    synthesize_mock = _patch_pipeline_dependencies(mocker, duplicate_match=low_confidence_match)

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content == _synthesized_result()
    synthesize_mock.assert_called_once()


def test_duplicate_check_failure_falls_back_to_the_full_pipeline(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value=_youtube_data())
    mocker.patch.object(service, "generate_embedding", side_effect=RuntimeError("embedding API down"))
    mocker.patch.object(service.musicbrainz, "search", return_value=[])
    mocker.patch.object(service.wikidata, "search", return_value=[])
    mocker.patch.object(service.wikipedia, "search", return_value=[])
    synthesize_mock = mocker.patch.object(service, "synthesize", return_value=_synthesized_result())

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content == _synthesized_result()
    synthesize_mock.assert_called_once()


def test_resolve_metadata_returns_error_status_when_the_full_pipeline_raises(mocker):
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", side_effect=RuntimeError("YouTube API down"))

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "ERROR"
    assert result.content is None
