import logging

from app.metadata import content_safety
from app.metadata.abuse_events import FLAGGED_INJECTION_EVENT
from app.metadata.content_safety import ContentSafetyOutcome
from app.metadata.schemas import RejectionReason, SubmissionPreCheckResult


def _precheck(**overrides) -> SubmissionPreCheckResult:
    fields = {
        "title": "Clean Song Title",
        "main_artists": ["Real Artist"],
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


def test_clean_music_passes_the_gate():
    outcome = content_safety.evaluate_precheck(_precheck(), "Clean Song Title", "Real Artist")

    assert outcome == ContentSafetyOutcome(rejected=False)


def test_flagged_injection_rejects_outright():
    precheck = _precheck(
        contains_injection_attempt=True,
        injection_reasoning="Text instructs the model to ignore prior instructions.",
    )

    outcome = content_safety.evaluate_precheck(precheck, "Clean Song Title", "Real Artist")

    assert outcome.rejected is True
    assert outcome.rejection_reason == RejectionReason.PROMPT_INJECTION


def test_flagged_injection_emits_the_stubbed_abuse_event(caplog):
    precheck = _precheck(contains_injection_attempt=True)

    with caplog.at_level(logging.WARNING, logger="app.metadata.abuse_events"):
        content_safety.evaluate_precheck(precheck, "Clean Song Title", "Real Artist")

    assert any(FLAGGED_INJECTION_EVENT in record.getMessage() for record in caplog.records)


def test_compilation_is_rejected():
    outcome = content_safety.evaluate_precheck(_precheck(is_compilation=True), "Title", "Artist")

    assert outcome.rejected is True
    assert outcome.rejection_reason == RejectionReason.COMPILATION


def test_non_music_classification_is_rejected():
    outcome = content_safety.evaluate_precheck(_precheck(is_song=False), "Title", "Artist")

    assert outcome.rejected is True
    assert outcome.rejection_reason == RejectionReason.NOT_MUSIC


def test_injection_is_checked_before_classification():
    """A flagged injection attempt rejects for that reason even when the
    classification fields alone would also have failed, matching the
    priority order the original two-call gate had."""
    precheck = _precheck(contains_injection_attempt=True, is_compilation=True)

    outcome = content_safety.evaluate_precheck(precheck, "Title", "Artist")

    assert outcome.rejection_reason == RejectionReason.PROMPT_INJECTION


def test_hard_filter_rejects_non_music_category_and_out_of_range_duration():
    assert content_safety.fails_hard_filter("22", 3600) is True


def test_hard_filter_lower_boundary_stays_in_the_ambiguous_tier():
    assert content_safety.fails_hard_filter("22", content_safety.MINIMUM_SONG_DURATION_SECONDS) is False


def test_hard_filter_upper_boundary_stays_in_the_ambiguous_tier():
    assert content_safety.fails_hard_filter("22", content_safety.MAXIMUM_SONG_DURATION_SECONDS) is False


def test_hard_filter_just_below_lower_boundary_rejects():
    assert content_safety.fails_hard_filter("22", content_safety.MINIMUM_SONG_DURATION_SECONDS - 1) is True


def test_music_category_bypasses_the_hard_filter_regardless_of_duration():
    assert content_safety.fails_hard_filter(content_safety.YOUTUBE_MUSIC_CATEGORY_ID, 3600) is False


def test_unknown_duration_stays_in_the_ambiguous_tier():
    assert content_safety.fails_hard_filter("22", None) is False
