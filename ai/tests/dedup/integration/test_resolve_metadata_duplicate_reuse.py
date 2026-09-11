import pg8000.native
import pytest
from pgvector import Vector
from pgvector.pg8000 import register_vector

from app.dedup.database import _parse_database_url
from app.metadata import service

EMBEDDING_DIMENSIONS = 1536
SHARED_EMBEDDING = [0.25] * EMBEDDING_DIMENSIONS


@pytest.fixture
def existing_verified_song(postgres_connection_url):
    connection = pg8000.native.Connection(**_parse_database_url(postgres_connection_url))
    register_vector(connection)
    try:
        inserted_rows = connection.run(
            """
            INSERT INTO songs (title, release_year, gradient_color1, gradient_color2, verification_status, confidence, embedding)
            VALUES ('One More Time', 2000, '8B5CF6', 'EC4899', 'VERIFIED', 'high', :embedding)
            RETURNING id
            """,
            embedding=Vector(SHARED_EMBEDDING),
        )
        (inserted_row,) = inserted_rows
        (song_id,) = inserted_row
        connection.run(
            "INSERT INTO song_artists (song_id, name, role, display_order) VALUES (:song_id, 'Daft Punk', 'MAIN', 0)",
            song_id=song_id,
        )
    finally:
        connection.close()
    return song_id


def test_submitting_a_near_duplicate_song_reuses_verified_data_instead_of_running_the_llm(
    mocker, existing_verified_song
):
    mocker.patch.object(
        service.youtube,
        "fetch_youtube_metadata",
        return_value={
            "video_title": "One More Time (Official Video)",
            "channel_title": "Daft Punk",
            "upload_year": 2019,
            "description": "",
        },
    )
    # The real embedding call is mocked, but the rest of the duplicate-detection path
    # (normalization already ran, the SQL similarity query, and reconstructing the result
    # from the matched row) all run for real against the Postgres test container.
    mocker.patch.object(service, "generate_embedding", return_value=SHARED_EMBEDDING)
    musicbrainz_search = mocker.patch.object(service.musicbrainz, "search", return_value=[])
    wikidata_search = mocker.patch.object(service.wikidata, "search", return_value=[])
    wikipedia_search = mocker.patch.object(service.wikipedia, "search", return_value=[])
    synthesize_mock = mocker.patch.object(service, "synthesize")

    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")

    assert result.status == "SUCCESS"
    assert result.content.title == "One More Time"
    assert result.content.artist == "Daft Punk"
    assert result.content.release_year == 2000
    assert result.content.source == service.DUPLICATE_MATCH_SOURCE_LABEL
    synthesize_mock.assert_not_called()
    musicbrainz_search.assert_not_called()
    wikidata_search.assert_not_called()
    wikipedia_search.assert_not_called()
