from unittest.mock import MagicMock

import pytest

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
