import logging
from datetime import date, datetime, timezone
from unittest.mock import MagicMock

import pytest
from openai.types import CreateEmbeddingResponse
from openai.types.chat import ChatCompletion

from app.usage import recorder

USAGE_DAY = date(2026, 10, 16)
LEAKED_SECRET = "s3cret-database-password"
LEAKED_API_KEY = "AIzaLeakedYoutubeKey"
CHAT_PROMPT_TOKENS = 812
CHAT_COMPLETION_TOKENS = 64
EMBEDDING_PROMPT_TOKENS = 9


class InlineUsageWriter:
    def submit(self, function, *arguments):
        function(*arguments)


class RejectingUsageWriter:
    def submit(self, function, *arguments):
        raise RuntimeError("cannot schedule new futures after shutdown")


def _sample_chat_completion(usage: dict | None) -> ChatCompletion:
    payload = {
        "id": "chatcmpl-sample",
        "object": "chat.completion",
        "created": 1_790_000_000,
        "model": "deepseek-ai/DeepSeek-V4-Flash",
        "choices": [
            {"index": 0, "finish_reason": "stop", "message": {"role": "assistant", "content": "{}"}}
        ],
    }
    if usage is not None:
        payload["usage"] = usage
    return ChatCompletion.model_validate(payload)


def _sample_embedding_response(usage: dict) -> CreateEmbeddingResponse:
    return CreateEmbeddingResponse.model_validate({
        "object": "list",
        "model": "text-embedding-3-small",
        "data": [{"object": "embedding", "index": 0, "embedding": [0.1, 0.2]}],
        "usage": usage,
    })


@pytest.mark.parametrize(
    ("moment", "expected_pacific_date", "expected_utc_date"),
    [
        # Standard time, UTC-8: 07:30 UTC is still the previous Pacific day.
        (datetime(2026, 3, 8, 7, 30, tzinfo=timezone.utc), date(2026, 3, 7), date(2026, 3, 8)),
        # Daylight time from 2026-03-08, UTC-7: Pacific midnight is 07:00 UTC.
        (datetime(2026, 3, 9, 6, 30, tzinfo=timezone.utc), date(2026, 3, 8), date(2026, 3, 9)),
        (datetime(2026, 3, 9, 7, 30, tzinfo=timezone.utc), date(2026, 3, 9), date(2026, 3, 9)),
        # Back to standard time from 2026-11-01: Pacific midnight is 08:00 UTC again.
        (datetime(2026, 11, 2, 7, 30, tzinfo=timezone.utc), date(2026, 11, 1), date(2026, 11, 2)),
        (datetime(2026, 11, 2, 8, 30, tzinfo=timezone.utc), date(2026, 11, 2), date(2026, 11, 2)),
    ],
)
def test_youtube_usage_follows_the_pacific_date_and_others_follow_utc(moment, expected_pacific_date, expected_utc_date):
    assert recorder.usage_date(recorder.PROVIDER_YOUTUBE, moment) == expected_pacific_date
    assert recorder.usage_date(recorder.PROVIDER_DEEPINFRA, moment) == expected_utc_date
    assert recorder.usage_date(recorder.PROVIDER_OPENAI, moment) == expected_utc_date


@pytest.mark.parametrize(
    ("operation", "expected_units"),
    [(recorder.OPERATION_YOUTUBE_VIDEOS_LIST, 1), (recorder.OPERATION_YOUTUBE_PLAYLIST_ITEMS_LIST, 1)],
)
def test_youtube_quota_units_per_endpoint(operation, expected_units):
    assert recorder.youtube_quota_units(operation) == expected_units


def test_chat_completion_tokens_come_from_the_usage_field():
    completion = _sample_chat_completion({
        "prompt_tokens": CHAT_PROMPT_TOKENS,
        "completion_tokens": CHAT_COMPLETION_TOKENS,
        "total_tokens": CHAT_PROMPT_TOKENS + CHAT_COMPLETION_TOKENS,
    })

    assert recorder.chat_completion_tokens(completion) == (CHAT_PROMPT_TOKENS, CHAT_COMPLETION_TOKENS)


@pytest.mark.parametrize("completion", [_sample_chat_completion(None), MagicMock(), None])
def test_chat_completion_without_usable_usage_counts_zero_tokens(completion):
    assert recorder.chat_completion_tokens(completion) == (0, 0)


def test_embedding_tokens_come_from_the_usage_field():
    response = _sample_embedding_response({"prompt_tokens": EMBEDDING_PROMPT_TOKENS, "total_tokens": EMBEDDING_PROMPT_TOKENS})

    assert recorder.embedding_tokens(response) == EMBEDDING_PROMPT_TOKENS


def test_embedding_tokens_fall_back_to_total_tokens():
    response = _sample_embedding_response({"prompt_tokens": 0, "total_tokens": EMBEDDING_PROMPT_TOKENS})

    assert recorder.embedding_tokens(response) == EMBEDDING_PROMPT_TOKENS


def test_persist_usage_upserts_one_row_and_closes_the_connection(mocker):
    connection = MagicMock()
    mocker.patch.object(recorder, "get_connection", return_value=connection)
    increment = recorder.UsageIncrement(
        usage_date=USAGE_DAY,
        provider=recorder.PROVIDER_DEEPINFRA,
        operation=recorder.OPERATION_CHAT_COMPLETION,
        input_tokens=CHAT_PROMPT_TOKENS,
        output_tokens=CHAT_COMPLETION_TOKENS,
    )

    recorder.persist_usage(increment)

    connection.run.assert_called_once_with(
        recorder.UPSERT_USAGE_SQL,
        usage_date=USAGE_DAY,
        provider=recorder.PROVIDER_DEEPINFRA,
        operation=recorder.OPERATION_CHAT_COMPLETION,
        call_count=1,
        quota_units=0,
        input_tokens=CHAT_PROMPT_TOKENS,
        output_tokens=CHAT_COMPLETION_TOKENS,
    )
    connection.close.assert_called_once()


def test_upsert_adds_to_the_existing_row_for_the_same_day_provider_and_operation():
    normalized_sql = " ".join(recorder.UPSERT_USAGE_SQL.split())

    assert "ON CONFLICT (usage_date, provider, operation) DO UPDATE SET" in normalized_sql
    for counter_column in ("call_count", "quota_units", "input_tokens", "output_tokens"):
        assert f"{counter_column} = api_usage_daily.{counter_column} + EXCLUDED.{counter_column}" in normalized_sql


def test_record_youtube_request_writes_the_endpoint_quota_on_the_pacific_day(mocker, monkeypatch):
    monkeypatch.setattr(recorder, "_usage_writer", InlineUsageWriter())
    persist_mock = mocker.patch.object(recorder, "persist_usage")

    recorder.record_youtube_request(recorder.OPERATION_YOUTUBE_PLAYLIST_ITEMS_LIST)

    (increment,) = persist_mock.call_args.args
    assert increment.provider == recorder.PROVIDER_YOUTUBE
    assert increment.operation == recorder.OPERATION_YOUTUBE_PLAYLIST_ITEMS_LIST
    assert increment.call_count == 1
    assert increment.quota_units == 1
    assert increment.usage_date == recorder.usage_date(recorder.PROVIDER_YOUTUBE, datetime.now(timezone.utc))


def test_record_chat_completion_and_embeddings_write_their_tokens(mocker, monkeypatch):
    monkeypatch.setattr(recorder, "_usage_writer", InlineUsageWriter())
    persist_mock = mocker.patch.object(recorder, "persist_usage")
    completion = _sample_chat_completion({
        "prompt_tokens": CHAT_PROMPT_TOKENS,
        "completion_tokens": CHAT_COMPLETION_TOKENS,
        "total_tokens": CHAT_PROMPT_TOKENS + CHAT_COMPLETION_TOKENS,
    })

    recorder.record_chat_completion(recorder.PROVIDER_OPENAI, completion)
    recorder.record_embeddings(_sample_embedding_response({"prompt_tokens": EMBEDDING_PROMPT_TOKENS, "total_tokens": EMBEDDING_PROMPT_TOKENS}))

    chat_increment, embedding_increment = (recorded_call.args[0] for recorded_call in persist_mock.call_args_list)
    assert (chat_increment.provider, chat_increment.operation) == (recorder.PROVIDER_OPENAI, recorder.OPERATION_CHAT_COMPLETION)
    assert (chat_increment.input_tokens, chat_increment.output_tokens) == (CHAT_PROMPT_TOKENS, CHAT_COMPLETION_TOKENS)
    assert (embedding_increment.provider, embedding_increment.operation) == (recorder.PROVIDER_OPENAI, recorder.OPERATION_EMBEDDINGS)
    assert (embedding_increment.input_tokens, embedding_increment.output_tokens) == (EMBEDDING_PROMPT_TOKENS, 0)


def test_a_failing_database_write_never_raises_or_logs_secrets(mocker, monkeypatch, caplog):
    monkeypatch.setattr(recorder, "_usage_writer", InlineUsageWriter())
    mocker.patch.object(
        recorder,
        "get_connection",
        side_effect=ConnectionError(f"postgresql://core:{LEAKED_SECRET}@db.example.test/core?key={LEAKED_API_KEY}"),
    )

    with caplog.at_level(logging.WARNING, logger=recorder.__name__):
        recorder.record_youtube_request(recorder.OPERATION_YOUTUBE_VIDEOS_LIST)

    assert "ConnectionError" in caplog.text
    assert LEAKED_SECRET not in caplog.text
    assert LEAKED_API_KEY not in caplog.text


def test_a_failing_statement_still_closes_the_connection_and_never_raises(mocker, monkeypatch):
    monkeypatch.setattr(recorder, "_usage_writer", InlineUsageWriter())
    connection = MagicMock()
    connection.run.side_effect = RuntimeError(LEAKED_SECRET)
    mocker.patch.object(recorder, "get_connection", return_value=connection)

    recorder.record_embeddings(_sample_embedding_response({"prompt_tokens": EMBEDDING_PROMPT_TOKENS, "total_tokens": EMBEDDING_PROMPT_TOKENS}))

    connection.close.assert_called_once()


def test_a_writer_that_cannot_schedule_never_raises(monkeypatch, caplog):
    monkeypatch.setattr(recorder, "_usage_writer", RejectingUsageWriter())

    with caplog.at_level(logging.WARNING, logger=recorder.__name__):
        recorder.record_chat_completion(recorder.PROVIDER_DEEPINFRA, None)

    assert "RuntimeError" in caplog.text


def test_an_unknown_youtube_operation_is_logged_instead_of_raised(caplog):
    with caplog.at_level(logging.WARNING, logger=recorder.__name__):
        recorder.record_youtube_request("unknown.list")

    assert "KeyError" in caplog.text
