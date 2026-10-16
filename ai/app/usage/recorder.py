"""Records external API usage into the core-owned api_usage_daily table.

Recording never affects the pipeline: the write runs on a background thread,
and every failure is logged by exception type only and then dropped.
"""

import logging
from concurrent.futures import Executor, ThreadPoolExecutor
from dataclasses import dataclass
from datetime import date, datetime, timezone
from zoneinfo import ZoneInfo

from app.dedup.database import get_connection

logger = logging.getLogger(__name__)

PROVIDER_YOUTUBE = "YOUTUBE"
PROVIDER_DEEPINFRA = "DEEPINFRA"
PROVIDER_OPENAI = "OPENAI"

OPERATION_CHAT_COMPLETION = "chat_completion"
OPERATION_EMBEDDINGS = "embeddings"
OPERATION_YOUTUBE_VIDEOS_LIST = "videos.list"
OPERATION_YOUTUBE_PLAYLIST_ITEMS_LIST = "playlistItems.list"

# https://developers.google.com/youtube/v3/determine_quota_cost
YOUTUBE_QUOTA_UNITS_BY_OPERATION = {
    OPERATION_YOUTUBE_VIDEOS_LIST: 1,
    OPERATION_YOUTUBE_PLAYLIST_ITEMS_LIST: 1,
}

YOUTUBE_QUOTA_TIME_ZONE = ZoneInfo("America/Los_Angeles")
SINGLE_CALL = 1
NO_USAGE = 0
USAGE_WRITER_THREADS = 1

UPSERT_USAGE_SQL = """
    INSERT INTO api_usage_daily
        (usage_date, provider, operation, call_count, quota_units, input_tokens, output_tokens)
    VALUES (:usage_date, :provider, :operation, :call_count, :quota_units, :input_tokens, :output_tokens)
    ON CONFLICT (usage_date, provider, operation) DO UPDATE SET
        call_count = api_usage_daily.call_count + EXCLUDED.call_count,
        quota_units = api_usage_daily.quota_units + EXCLUDED.quota_units,
        input_tokens = api_usage_daily.input_tokens + EXCLUDED.input_tokens,
        output_tokens = api_usage_daily.output_tokens + EXCLUDED.output_tokens,
        updated_at = now()
"""


@dataclass(frozen=True)
class UsageIncrement:
    usage_date: date
    provider: str
    operation: str
    call_count: int = SINGLE_CALL
    quota_units: int = NO_USAGE
    input_tokens: int = NO_USAGE
    output_tokens: int = NO_USAGE


_usage_writer: Executor = ThreadPoolExecutor(max_workers=USAGE_WRITER_THREADS, thread_name_prefix="api-usage")


def usage_date(provider: str, moment: datetime) -> date:
    """YouTube's daily quota resets at midnight Pacific time, so YouTube usage
    belongs to the Pacific date; every other provider uses the UTC date."""
    time_zone = YOUTUBE_QUOTA_TIME_ZONE if provider == PROVIDER_YOUTUBE else timezone.utc
    return moment.astimezone(time_zone).date()


def youtube_quota_units(operation: str) -> int:
    return YOUTUBE_QUOTA_UNITS_BY_OPERATION[operation]


def _token_count(usage: object, field_name: str) -> int:
    token_count = getattr(usage, field_name, None) if usage is not None else None
    if isinstance(token_count, bool) or not isinstance(token_count, int) or token_count < NO_USAGE:
        return NO_USAGE
    return token_count


def chat_completion_tokens(completion: object) -> tuple[int, int]:
    """Input and output tokens from a chat completion's usage field, zero when absent."""
    usage = getattr(completion, "usage", None)
    return _token_count(usage, "prompt_tokens"), _token_count(usage, "completion_tokens")


def embedding_tokens(response: object) -> int:
    """Input tokens from an embeddings response's usage field, zero when absent."""
    usage = getattr(response, "usage", None)
    return _token_count(usage, "prompt_tokens") or _token_count(usage, "total_tokens")


def persist_usage(increment: UsageIncrement) -> None:
    connection = get_connection()
    try:
        connection.run(
            UPSERT_USAGE_SQL,
            usage_date=increment.usage_date,
            provider=increment.provider,
            operation=increment.operation,
            call_count=increment.call_count,
            quota_units=increment.quota_units,
            input_tokens=increment.input_tokens,
            output_tokens=increment.output_tokens,
        )
    finally:
        connection.close()


def _log_unrecorded_usage(provider: str, operation: str, recording_error: Exception) -> None:
    logger.warning("API usage for %s %s was not recorded: %s", provider, operation, type(recording_error).__name__)


def _persist_usage_safely(increment: UsageIncrement) -> None:
    try:
        persist_usage(increment)
    except Exception as recording_error:
        _log_unrecorded_usage(increment.provider, increment.operation, recording_error)


def record_usage(
    provider: str,
    operation: str,
    *,
    quota_units: int = NO_USAGE,
    input_tokens: int = NO_USAGE,
    output_tokens: int = NO_USAGE,
) -> None:
    try:
        increment = UsageIncrement(
            usage_date=usage_date(provider, datetime.now(timezone.utc)),
            provider=provider,
            operation=operation,
            quota_units=quota_units,
            input_tokens=input_tokens,
            output_tokens=output_tokens,
        )
        _usage_writer.submit(_persist_usage_safely, increment)
    except Exception as recording_error:
        _log_unrecorded_usage(provider, operation, recording_error)


def record_youtube_request(operation: str) -> None:
    """Counts one YouTube request that received a response, whatever its status."""
    try:
        quota_units = youtube_quota_units(operation)
    except Exception as recording_error:
        _log_unrecorded_usage(PROVIDER_YOUTUBE, operation, recording_error)
        return
    record_usage(PROVIDER_YOUTUBE, operation, quota_units=quota_units)


def record_chat_completion(provider: str, completion: object) -> None:
    try:
        input_tokens, output_tokens = chat_completion_tokens(completion)
    except Exception as recording_error:
        _log_unrecorded_usage(provider, OPERATION_CHAT_COMPLETION, recording_error)
        return
    record_usage(provider, OPERATION_CHAT_COMPLETION, input_tokens=input_tokens, output_tokens=output_tokens)


def record_embeddings(response: object) -> None:
    try:
        input_tokens = embedding_tokens(response)
    except Exception as recording_error:
        _log_unrecorded_usage(PROVIDER_OPENAI, OPERATION_EMBEDDINGS, recording_error)
        return
    record_usage(PROVIDER_OPENAI, OPERATION_EMBEDDINGS, input_tokens=input_tokens)
