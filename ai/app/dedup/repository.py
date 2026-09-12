from pgvector import Vector

from app.dedup.database import get_connection
from app.dedup.schemas import VerifiedSongMatch

VERIFICATION_STATUS_VERIFIED = "VERIFIED"

_FIND_BEST_VERIFIED_MATCH_QUERY = """
    SELECT s.id AS id,
           string_agg(song_artists.name, ', ' ORDER BY song_artists.display_order) AS artist,
           s.title AS title,
           s.release_year AS release_year,
           s.gradient_color1 AS gradient_color1,
           s.gradient_color2 AS gradient_color2,
           s.confidence AS confidence,
           s.embedding <=> :query_embedding AS cosine_distance
    FROM songs s
    JOIN song_artists ON song_artists.song_id = s.id
    WHERE s.verification_status = :verified_status AND s.embedding IS NOT NULL
    GROUP BY s.id
    ORDER BY cosine_distance ASC
    LIMIT 1
"""

_STORE_VERIFIED_SONG_EMBEDDING_QUERY = """
    UPDATE songs
    SET embedding = :embedding
    WHERE id = :song_id AND verification_status = :verified_status
"""


def _rows_as_dicts(connection, rows) -> list[dict]:
    column_names = [column["name"] for column in connection.columns]
    return [dict(zip(column_names, row)) for row in rows]


def find_best_verified_match(embedding: list[float]) -> VerifiedSongMatch | None:
    """Finds the closest verified song to the given embedding by cosine
    distance, if any verified song has a stored embedding at all. The caller
    decides what distance counts as a high-confidence match."""
    connection = get_connection()
    try:
        rows = connection.run(
            _FIND_BEST_VERIFIED_MATCH_QUERY,
            query_embedding=Vector(embedding),
            verified_status=VERIFICATION_STATUS_VERIFIED,
        )
        matching_rows = _rows_as_dicts(connection, rows)
    finally:
        connection.close()

    if not matching_rows:
        return None

    (best_match_row,) = matching_rows
    return VerifiedSongMatch(**best_match_row)


def store_verified_song_embedding(song_id: int, embedding: list[float]) -> None:
    """Stores an embedding for an already-verified song. A no-op if the song
    isn't verified, this never backfills an embedding onto unverified data."""
    connection = get_connection()
    try:
        connection.run(
            _STORE_VERIFIED_SONG_EMBEDDING_QUERY,
            embedding=Vector(embedding),
            song_id=song_id,
            verified_status=VERIFICATION_STATUS_VERIFIED,
        )
    finally:
        connection.close()
