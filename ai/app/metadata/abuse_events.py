import logging

logger = logging.getLogger(__name__)

FLAGGED_INJECTION_EVENT = "flagged_injection_attempt"


def record_flagged_injection_attempt(video_title: str, channel_title: str, detail: str) -> None:
    """Records that a submission was flagged as a prompt-injection attempt.

    Story 34's usage-analytics event pipeline (Phase 3) is not built, so this
    writes a structured log line rather than a real event. Story 34 replaces
    this with a write into its event store.
    """
    # TODO: story 34, replace this log line with a real abuse-visibility event write.
    logger.warning(
        "abuse_event=%s video_title=%r channel_title=%r detail=%r",
        FLAGGED_INJECTION_EVENT,
        video_title,
        channel_title,
        detail,
    )
