from unittest.mock import MagicMock

from app.clients.openai_client import client
from app.config import settings
from app.dedup import embedding_client


def test_generate_embedding_returns_the_first_result_vector(mocker):
    expected_vector = [0.1, 0.2, 0.3]
    mock_response = MagicMock(data=[MagicMock(embedding=expected_vector)])
    mocker.patch.object(client.embeddings, "create", return_value=mock_response)

    result = embedding_client.generate_embedding("daft punk one more time")

    assert result == expected_vector


def test_generate_embedding_uses_the_configured_embedding_model(mocker):
    mock_response = MagicMock(data=[MagicMock(embedding=[0.0])])
    create_mock = mocker.patch.object(client.embeddings, "create", return_value=mock_response)

    embedding_client.generate_embedding("some text")

    create_mock.assert_called_once_with(model=settings.embedding_model, input="some text")
