from unittest.mock import MagicMock

import httpx
import pytest

from app.metadata.schemas import LlmExtractionResult, SongMetadataResult, SubmissionPreCheckResult


@pytest.mark.parametrize("confidence", ["HIGH", "Medium", "low", "unexpected"])
def test_metadata_schema_normalizes_confidence(confidence):
    extraction_result = LlmExtractionResult(
        title="Test Song", artist="Test Artist", release_year=1999, confidence=confidence, reasoning="Test reasoning."
    )
    metadata_result = SongMetadataResult(
        title="Test Song",
        artist="Test Artist",
        release_year=1999,
        color="8B5CF6",
        confidence=confidence,
        source="test",
        reasoning="Test reasoning.",
    )
    precheck_result = SubmissionPreCheckResult(
        title="Test Song",
        artist="Test Artist",
        color="8B5CF6",
        contains_injection_attempt=False,
        injection_reasoning="No injection.",
        is_song=True,
        is_compilation=False,
        classification_confidence=confidence,
        classification_reasoning="Song.",
    )

    expected_confidence = confidence.lower() if confidence.lower() in {"low", "medium", "high"} else "low"
    assert extraction_result.confidence == expected_confidence
    assert metadata_result.confidence == expected_confidence
    assert precheck_result.classification_confidence == expected_confidence
from openai import APIStatusError
from pydantic import BaseModel

from app.clients.deepinfra_client import client as deepinfra_client
from app.clients.openai_client import client
from app.metadata import llm


def _mock_completion(parsed):
    return MagicMock(choices=[MagicMock(message=MagicMock(parsed=parsed))])


class _WikipediaExtractionResult(BaseModel):
    release_year: int | None
    confidence: str


class _ReconciliationResult(BaseModel):
    release_year: int | None
    confidence: str
    reasoning: str


def _response_format_not_supported_error() -> APIStatusError:
    request = httpx.Request("POST", "https://api.deepinfra.com/v1/openai/chat/completions")
    response = httpx.Response(400, request=request)
    return APIStatusError(
        "response format is not supported for this model",
        response=response,
        body={"message": "response format is not supported for this model"},
    )


def test_extract_structured_returns_parsed_result_via_json_schema_mode(mocker):
    expected = _WikipediaExtractionResult(release_year=1999, confidence="high")
    mocker.patch.object(deepinfra_client.chat.completions, "parse", return_value=_mock_completion(expected))

    result = llm.extract_structured("some prompt", _WikipediaExtractionResult)

    assert result == expected


def test_extract_structured_works_with_a_differently_shaped_model(mocker):
    expected = _ReconciliationResult(release_year=2001, confidence="medium", reasoning="Sources disagree by a year.")
    mocker.patch.object(deepinfra_client.chat.completions, "parse", return_value=_mock_completion(expected))

    result = llm.extract_structured("some prompt", _ReconciliationResult)

    assert result == expected


def test_extract_structured_raises_when_response_does_not_match_schema(mocker):
    mocker.patch.object(deepinfra_client.chat.completions, "parse", return_value=_mock_completion(None))

    with pytest.raises(ValueError):
        llm.extract_structured("some prompt", _WikipediaExtractionResult)


def test_extract_structured_falls_back_to_tool_call_when_response_format_unsupported(mocker):
    mocker.patch.object(deepinfra_client.chat.completions, "parse", side_effect=_response_format_not_supported_error())
    expected = _WikipediaExtractionResult(release_year=1999, confidence="high")
    tool_call = MagicMock(function=MagicMock(arguments=expected.model_dump_json()))
    mocker.patch.object(
        deepinfra_client.chat.completions,
        "create",
        return_value=MagicMock(choices=[MagicMock(message=MagicMock(tool_calls=[tool_call]))]),
    )

    result = llm.extract_structured("some prompt", _WikipediaExtractionResult)

    assert result == expected


def test_extract_structured_reraises_unrelated_api_errors(mocker):
    request = httpx.Request("POST", "https://api.deepinfra.com/v1/openai/chat/completions")
    response = httpx.Response(500, request=request)
    unrelated_error = APIStatusError("server error", response=response, body={"message": "internal server error"})
    mocker.patch.object(deepinfra_client.chat.completions, "parse", side_effect=unrelated_error)

    with pytest.raises(APIStatusError):
        llm.extract_structured("some prompt", _WikipediaExtractionResult)


def test_extract_structured_raises_when_tool_call_fallback_gets_no_tool_calls(mocker):
    mocker.patch.object(deepinfra_client.chat.completions, "parse", side_effect=_response_format_not_supported_error())
    mocker.patch.object(
        deepinfra_client.chat.completions,
        "create",
        return_value=MagicMock(choices=[MagicMock(message=MagicMock(tool_calls=[]))]),
    )

    with pytest.raises(ValueError):
        llm.extract_structured("some prompt", _WikipediaExtractionResult)


class _ReconciliationSchema(BaseModel):
    release_year: int | None
    confidence: str
    reasoning: str


def test_synthesize_with_model_returns_parsed_result(mocker):
    expected = _ReconciliationSchema(release_year=1991, confidence="high", reasoning="Two sources agree.")
    parse_mock = mocker.patch.object(client.chat.completions, "parse", return_value=_mock_completion(expected))

    result = llm.synthesize_with_model("some prompt", "gpt-5-nano", _ReconciliationSchema)

    assert result == expected


def test_synthesize_with_model_does_not_pass_an_explicit_temperature(mocker):
    """gpt-5-nano rejects any temperature other than its default (1) with a 400."""
    parse_mock = mocker.patch.object(
        client.chat.completions, "parse", return_value=_mock_completion(_ReconciliationSchema(
            release_year=1991, confidence="high", reasoning="Two sources agree."
        ))
    )

    llm.synthesize_with_model("some prompt", "gpt-5-nano", _ReconciliationSchema)

    _call_arguments, call_keyword_arguments = parse_mock.call_args
    assert "temperature" not in call_keyword_arguments


def test_synthesize_with_model_raises_when_response_does_not_match_schema(mocker):
    mocker.patch.object(client.chat.completions, "parse", return_value=_mock_completion(None))

    with pytest.raises(ValueError):
        llm.synthesize_with_model("some prompt", "gpt-5-nano", _ReconciliationSchema)


def test_synthesize_with_model_reraises_and_reports_an_api_failure(mocker):
    request = httpx.Request("POST", "https://api.openai.com/v1/chat/completions")
    response = httpx.Response(400, request=request)
    api_error = APIStatusError(
        "temperature not supported", response=response, body={"message": "temperature not supported"}
    )
    mocker.patch.object(client.chat.completions, "parse", side_effect=api_error)
    report_mock = mocker.patch("app.metadata.llm.report_openai_failure")

    with pytest.raises(APIStatusError):
        llm.synthesize_with_model("some prompt", "gpt-5-nano", _ReconciliationSchema)

    report_mock.assert_called_once_with(api_error)
