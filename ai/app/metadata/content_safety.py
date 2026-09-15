import logging
from dataclasses import dataclass

from app.metadata import abuse_events, content_safety_prompts
from app.metadata.llm import extract_structured
from app.metadata.schemas import InjectionCheckResult, RejectionReason, SongClassification

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


def _fails_hard_filter(category_id: str, duration_seconds: int | None) -> bool:
    return category_id != YOUTUBE_MUSIC_CATEGORY_ID and not _is_song_length(duration_seconds)


def check_for_injection(video_title: str, channel_title: str, description: str) -> InjectionCheckResult:
    prompt = content_safety_prompts.build_injection_check_prompt(video_title, channel_title, description)
    return extract_structured(prompt, InjectionCheckResult)


def classify_submission(
    video_title: str,
    channel_title: str,
    description: str,
    category_id: str,
    duration_seconds: int | None,
) -> SongClassification:
    prompt = content_safety_prompts.build_classification_prompt(
        video_title, channel_title, description, category_id, duration_seconds
    )
    return extract_structured(prompt, SongClassification)


def evaluate(youtube_data: dict[str, object]) -> ContentSafetyOutcome:
    """Runs the submission through the content-safety gate before the expensive
    metadata pipeline. A flagged prompt-injection attempt is rejected outright
    and recorded as an abuse-visibility event. A hard duration-and-category
    mismatch is rejected without an LLM call. Otherwise an LLM classification
    decides non-music and compilation rejections, escalating a genuinely
    uncertain case to manual review rather than rejecting it."""
    video_title = str(youtube_data.get("video_title", ""))
    channel_title = str(youtube_data.get("channel_title", ""))
    description = str(youtube_data.get("description", ""))
    category_id = str(youtube_data.get("category_id", "unknown"))
    duration_seconds = youtube_data.get("duration_seconds")

    injection_result = check_for_injection(video_title, channel_title, description)
    if injection_result.contains_injection_attempt:
        abuse_events.record_flagged_injection_attempt(video_title, channel_title, injection_result.reasoning)
        return _rejected(RejectionReason.PROMPT_INJECTION, INJECTION_REJECTION_DETAIL)

    if _fails_hard_filter(category_id, duration_seconds):
        return _rejected(RejectionReason.NOT_MUSIC, NOT_MUSIC_REJECTION_DETAIL)

    classification = classify_submission(video_title, channel_title, description, category_id, duration_seconds)
    if classification.is_compilation:
        return _rejected(RejectionReason.COMPILATION, COMPILATION_REJECTION_DETAIL)
    if not classification.is_song:
        return _rejected(RejectionReason.NOT_MUSIC, NOT_MUSIC_REJECTION_DETAIL)

    return PASSED_OUTCOME
