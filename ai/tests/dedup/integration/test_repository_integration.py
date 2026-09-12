import pg8000.native
import pytest
from pgvector import Vector
from pgvector.pg8000 import register_vector

from app.dedup import repository
from app.dedup.database import _parse_database_url

EMBEDDING_DIMENSIONS = 1536

_INSERT_SONG_QUERY = """
    INSERT INTO songs (title, release_year, gradient_color1, gradient_color2, verification_status, confidence, embedding)
    VALUES (:title, :release_year, '8B5CF6', 'EC4899', :verification_status, 'high', :embedding)
    RETURNING id
"""
_INSERT_SONG_ARTIST_QUERY = """
    INSERT INTO song_artists (song_id, name, role, display_order) VALUES (:song_id, :name, 'MAIN', 0)
"""


def _embedding(seed_value: float) -> list[float]:
    return [seed_value] * EMBEDDING_DIMENSIONS


def _insert_song(connection, *, title, release_year, artist_name, verification_status, embedding=None):
    inserted_rows = connection.run(
        _INSERT_SONG_QUERY,
        title=title,
        release_year=release_year,
        verification_status=verification_status,
        embedding=Vector(embedding) if embedding is not None else None,
    )
    (inserted_row,) = inserted_rows
    (song_id,) = inserted_row

    connection.run(_INSERT_SONG_ARTIST_QUERY, song_id=song_id, name=artist_name)
    return song_id


@pytest.fixture
def db_connection(postgres_connection_url):
    connection = pg8000.native.Connection(**_parse_database_url(postgres_connection_url))
    register_vector(connection)
    yield connection
    connection.close()


def test_finds_the_closest_verified_song_within_a_real_database(db_connection):
    near_duplicate_embedding = _embedding(1.0)
    unrelated_embedding = _embedding(-1.0)

    matching_song_id = _insert_song(
        db_connection,
        title="One More Time",
        release_year=2000,
        artist_name="Daft Punk",
        verification_status="VERIFIED",
        embedding=near_duplicate_embedding,
    )
    _insert_song(
        db_connection,
        title="A Completely Different Song",
        release_year=2015,
        artist_name="Someone Else",
        verification_status="VERIFIED",
        embedding=unrelated_embedding,
    )

    match = repository.find_best_verified_match(near_duplicate_embedding)

    assert match is not None
    assert match.id == matching_song_id
    assert match.artist == "Daft Punk"
    assert match.title == "One More Time"
    assert match.cosine_distance == pytest.approx(0.0, abs=1e-6)


def test_ignores_unverified_songs_even_with_a_close_embedding(db_connection):
    embedding = _embedding(1.0)
    _insert_song(
        db_connection,
        title="One More Time",
        release_year=2000,
        artist_name="Daft Punk",
        verification_status="UNVERIFIED",
        embedding=embedding,
    )

    match = repository.find_best_verified_match(embedding)

    assert match is None


def test_ignores_verified_songs_with_no_stored_embedding(db_connection):
    _insert_song(
        db_connection,
        title="One More Time",
        release_year=2000,
        artist_name="Daft Punk",
        verification_status="VERIFIED",
        embedding=None,
    )

    match = repository.find_best_verified_match(_embedding(1.0))

    assert match is None


def test_store_verified_song_embedding_persists_only_for_verified_songs(db_connection):
    embedding = _embedding(0.5)
    verified_song_id = _insert_song(
        db_connection,
        title="Verified Song",
        release_year=2010,
        artist_name="Some Artist",
        verification_status="VERIFIED",
    )
    unverified_song_id = _insert_song(
        db_connection,
        title="Unverified Song",
        release_year=2011,
        artist_name="Another Artist",
        verification_status="UNVERIFIED",
    )

    repository.store_verified_song_embedding(verified_song_id, embedding)
    repository.store_verified_song_embedding(unverified_song_id, embedding)

    rows = db_connection.run("SELECT id FROM songs WHERE embedding IS NOT NULL")
    song_ids_with_embeddings = {song_id for (song_id,) in rows}

    assert song_ids_with_embeddings == {verified_song_id}
