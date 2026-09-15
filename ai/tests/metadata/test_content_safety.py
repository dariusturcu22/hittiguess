import logging

from app.metadata import content_safety
from app.metadata.abuse_events import FLAGGED_INJECTION_EVENT
from app.metadata.schemas import InjectionCheckResult, RejectionReason, SongClassification


def _youtube_data(**overrides):
    data = {
        "video_title": "Clean Song Title",
        "channel_title": "Real Artist",
        "description": "A normal song description.",
        "category_id": content_safety.YOUTUBE_MUSIC_CATEGORY_ID,
        "duration_seconds": 210,
    }
    data.update(overrides)
    return data


def _no_injection():
    return InjectionCheckResult(contains_injection_attempt=False, reasoning="Ordinary song text.")


def _injection():
    return InjectionCheckResult(
        contains_injection_attempt=True,
        reasoning="Text instructs the model to ignore prior instructions.",
    )


def _single_song():
    return SongClassification(is_song=True, is_compilation=False, confidence="high", reasoning="Single track.")


def _compilation():
    return SongClassification(is_song=True, is_compilation=True, confidence="high", reasoning="A multi-song mix.")


def _not_music():
    return SongClassification(is_song=False, is_compilation=False, confidence="high", reasoning="Spoken-word podcast.")


def _patch_checks(mocker, injection_result, classification_result=None):
    mocker.patch.object(content_safety, "check_for_injection", return_value=injection_result)
    return mocker.patch.object(content_safety, "classify_submission", return_value=classification_result)


def test_clean_music_passes_the_gate(mocker):
    classify_mock = _patch_checks(mocker, _no_injection(), _single_song())

    outcome = content_safety.evaluate(_youtube_data())

    assert outcome.rejected is False
    assert outcome.rejection_reason is None
    classify_mock.assert_called_once()


def test_flagged_injection_rejects_outright(mocker):
    classify_mock = _patch_checks(mocker, _injection(), _single_song())

    outcome = content_safety.evaluate(_youtube_data())

    assert outcome.rejected is True
    assert outcome.rejection_reason == RejectionReason.PROMPT_INJECTION


def test_flagged_injection_skips_classification(mocker):
    classify_mock = _patch_checks(mocker, _injection(), _single_song())

    content_safety.evaluate(_youtube_data())

    classify_mock.assert_not_called()


def test_flagged_injection_emits_the_stubbed_abuse_event(mocker, caplog):
    _patch_checks(mocker, _injection(), _single_song())

    with caplog.at_level(logging.WARNING, logger="app.metadata.abuse_events"):
        content_safety.evaluate(_youtube_data())

    assert any(FLAGGED_INJECTION_EVENT in record.getMessage() for record in caplog.records)


def test_compilation_is_rejected(mocker):
    _patch_checks(mocker, _no_injection(), _compilation())

    outcome = content_safety.evaluate(_youtube_data())

    assert outcome.rejected is True
    assert outcome.rejection_reason == RejectionReason.COMPILATION


def test_non_music_classification_is_rejected(mocker):
    _patch_checks(mocker, _no_injection(), _not_music())

    outcome = content_safety.evaluate(_youtube_data())

    assert outcome.rejected is True
    assert outcome.rejection_reason == RejectionReason.NOT_MUSIC


def test_hard_filter_rejects_non_music_category_and_out_of_range_duration(mocker):
    classify_mock = _patch_checks(mocker, _no_injection(), _single_song())

    outcome = content_safety.evaluate(_youtube_data(category_id="22", duration_seconds=3600))

    assert outcome.rejected is True
    assert outcome.rejection_reason == RejectionReason.NOT_MUSIC
    classify_mock.assert_not_called()


def test_hard_filter_lower_boundary_stays_in_the_ambiguous_tier(mocker):
    classify_mock = _patch_checks(mocker, _no_injection(), _single_song())

    outcome = content_safety.evaluate(
        _youtube_data(category_id="22", duration_seconds=content_safety.MINIMUM_SONG_DURATION_SECONDS)
    )

    assert outcome.rejected is False
    classify_mock.assert_called_once()


def test_hard_filter_upper_boundary_stays_in_the_ambiguous_tier(mocker):
    classify_mock = _patch_checks(mocker, _no_injection(), _single_song())

    outcome = content_safety.evaluate(
        _youtube_data(category_id="22", duration_seconds=content_safety.MAXIMUM_SONG_DURATION_SECONDS)
    )

    assert outcome.rejected is False
    classify_mock.assert_called_once()


def test_hard_filter_just_below_lower_boundary_rejects(mocker):
    classify_mock = _patch_checks(mocker, _no_injection(), _single_song())

    outcome = content_safety.evaluate(
        _youtube_data(category_id="22", duration_seconds=content_safety.MINIMUM_SONG_DURATION_SECONDS - 1)
    )

    assert outcome.rejected is True
    assert outcome.rejection_reason == RejectionReason.NOT_MUSIC
    classify_mock.assert_not_called()


def test_music_category_bypasses_the_hard_filter_regardless_of_duration(mocker):
    classify_mock = _patch_checks(mocker, _no_injection(), _single_song())

    outcome = content_safety.evaluate(
        _youtube_data(category_id=content_safety.YOUTUBE_MUSIC_CATEGORY_ID, duration_seconds=3600)
    )

    assert outcome.rejected is False
    classify_mock.assert_called_once()


def test_unknown_duration_stays_in_the_ambiguous_tier(mocker):
    classify_mock = _patch_checks(mocker, _no_injection(), _single_song())

    outcome = content_safety.evaluate(_youtube_data(category_id="22", duration_seconds=None))

    assert outcome.rejected is False
    classify_mock.assert_called_once()


def test_check_for_injection_uses_structured_output(mocker):
    extract_mock = mocker.patch.object(
        content_safety, "extract_structured", return_value=_no_injection()
    )

    result = content_safety.check_for_injection("title", "channel", "description")

    assert result == _no_injection()
    _, response_model = extract_mock.call_args.args
    assert response_model is InjectionCheckResult


def test_classify_submission_uses_structured_output(mocker):
    extract_mock = mocker.patch.object(
        content_safety, "extract_structured", return_value=_single_song()
    )

    result = content_safety.classify_submission("title", "channel", "description", "10", 210)

    assert result == _single_song()
    _, response_model = extract_mock.call_args.args
    assert response_model is SongClassification
