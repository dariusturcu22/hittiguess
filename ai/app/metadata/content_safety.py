import logging
from dataclasses import dataclass

from app.metadata import abuse_events
from app.metadata.schemas import RejectionReason, SubmissionPreCheckResult

logger = logging.getLogger(__name__)

YOUTUBE_MUSIC_CATEGORY_ID = "10"
MINIMUM_SONG_DURATION_SECONDS = 60
MAXIMUM_SONG_DURATION_SECONDS = 12 * 60

NOT_MUSIC_REJECTION_DETAIL = "The submission was classified as not a single musical track."
COMPILATION_REJECTION_DETAIL = (
    "The submission was classified as a compilation, mix, or full album rather than a single song."
)
INJECTION_REJECTION_DETAIL = (
    "The submission's YouTube text was flagged as a prompt-injection attempt."
)


@dataclass(frozen=True)
class ContentSafetyOutcome:
    rejected: bool
    rejection_reason: RejectionReason | None = None
    rejection_detail: str | None = None


PASSED_OUTCOME = ContentSafetyOutcome(rejected=False)


def _rejected(reason: RejectionReason, detail: str) -> ContentSafetyOutcome:
    return ContentSafetyOutcome(rejected=True, rejection_reason=reason, rejection_detail=detail)


def _is_song_length(duration_seconds: int | None) -> bool:
    if duration_seconds is None:
        return True
    return MINIMUM_SONG_DURATION_SECONDS <= duration_seconds <= MAXIMUM_SONG_DURATION_SECONDS


def fails_hard_filter(category_id: str, duration_seconds: int | None) -> bool:
    """A deterministic, LLM-free rejection: neither YouTube's own music
    category nor a song-length duration. Checked before the precheck LLM
    call even runs, so an obviously-not-a-song submission costs nothing."""
    return category_id != YOUTUBE_MUSIC_CATEGORY_ID and not _is_song_length(duration_seconds)


def evaluate_precheck(
    precheck: SubmissionPreCheckResult, video_title: str, channel_title: str
) -> ContentSafetyOutcome:
    """Turns the already-run precheck call's injection and classification
    fields into a rejection decision. Takes no LLM call of its own: the
    hard filter above already ran first, and the precheck call already
    happened once for the whole submission, shared with title/artist/color
    extraction rather than run again here. video_title/channel_title are
    the raw YouTube text, not the cleaned title/artist, for the abuse log."""
    if precheck.contains_injection_attempt:
        abuse_events.record_flagged_injection_attempt(video_title, channel_title, precheck.injection_reasoning)
        return _rejected(RejectionReason.PROMPT_INJECTION, INJECTION_REJECTION_DETAIL)

    if precheck.is_compilation:
        return _rejected(RejectionReason.COMPILATION, COMPILATION_REJECTION_DETAIL)
    if not precheck.is_song:
        return _rejected(RejectionReason.NOT_MUSIC, NOT_MUSIC_REJECTION_DETAIL)

    return PASSED_OUTCOME
