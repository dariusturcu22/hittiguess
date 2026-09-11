from unittest.mock import MagicMock

import httpx
import pytest
from openai import APIStatusError
from pydantic import BaseModel

from app.clients.deepinfra_client import client as deepinfra_client
from app.clients.openai_client import client
from app.metadata import llm
from app.metadata.schemas import SongMetadataResult


def _expected_result() -> SongMetadataResult:
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


def _mock_completion(parsed):
    return MagicMock(choices=[MagicMock(message=MagicMock(parsed=parsed))])


def test_synthesize_returns_parsed_result(mocker):
    expected = _expected_result()
    mocker.patch.object(client.chat.completions, "parse", return_value=_mock_completion(expected))

    result = llm.synthesize("some prompt")

    assert result == expected


def test_synthesize_raises_when_response_does_not_match_schema(mocker):
    mocker.patch.object(client.chat.completions, "parse", return_value=_mock_completion(None))

    with pytest.raises(ValueError):
        llm.synthesize("some prompt")


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
