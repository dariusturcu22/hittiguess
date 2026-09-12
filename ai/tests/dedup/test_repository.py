from unittest.mock import MagicMock

from app.dedup import repository
from app.dedup.schemas import VerifiedSongMatch


def _mock_connection(*, rows, columns):
    connection = MagicMock()
    connection.run.return_value = rows
    connection.columns = columns
    return connection


def test_find_best_verified_match_returns_none_when_no_row_matches(mocker):
    connection = _mock_connection(rows=[], columns=[])
    mocker.patch.object(repository, "get_connection", return_value=connection)

    result = repository.find_best_verified_match([0.1, 0.2, 0.3])

    assert result is None
    connection.close.assert_called_once()


def test_find_best_verified_match_builds_a_match_from_the_returned_row(mocker):
    expected_match = VerifiedSongMatch(
        id=42,
        artist="Daft Punk",
        title="One More Time",
        release_year=2000,
        gradient_color1="8B5CF6",
        gradient_color2="EC4899",
        confidence="high",
        cosine_distance=0.02,
    )
    field_names = VerifiedSongMatch.model_fields
    columns = [{"name": field_name} for field_name in field_names]
    row = tuple(getattr(expected_match, field_name) for field_name in field_names)
    connection = _mock_connection(rows=[row], columns=columns)
    mocker.patch.object(repository, "get_connection", return_value=connection)

    result = repository.find_best_verified_match([0.1, 0.2, 0.3])

    assert result == expected_match


def test_find_best_verified_match_only_considers_verified_songs(mocker):
    connection = _mock_connection(rows=[], columns=[])
    mocker.patch.object(repository, "get_connection", return_value=connection)

    repository.find_best_verified_match([0.1, 0.2, 0.3])

    query_params = connection.run.call_args.kwargs
    assert query_params["verified_status"] == repository.VERIFICATION_STATUS_VERIFIED


def test_store_verified_song_embedding_scopes_the_update_to_verified_songs(mocker):
    connection = _mock_connection(rows=[], columns=[])
    mocker.patch.object(repository, "get_connection", return_value=connection)

    repository.store_verified_song_embedding(song_id=7, embedding=[0.1, 0.2, 0.3])

    query_params = connection.run.call_args.kwargs
    assert query_params["song_id"] == 7
    assert query_params["verified_status"] == repository.VERIFICATION_STATUS_VERIFIED
    connection.close.assert_called_once()
