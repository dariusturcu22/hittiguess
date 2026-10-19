import logging
from dataclasses import dataclass

from pgvector import Vector
from pydantic import BaseModel

from app.dedup.database import get_connection
from app.dedup.embedding_client import generate_embedding
from app.dedup.normalize import normalize_artist_and_title
from app.dedup.repository import VERIFICATION_STATUS_VERIFIED

logger = logging.getLogger(__name__)
LEASE_SECONDS = 300
RETRY_BASE_SECONDS = 60
RETRY_MAX_SECONDS = 21600
RETRY_MAX_EXPONENT = 9
EMBEDDING_DIMENSIONS = 1536


@dataclass(frozen=True)
class IndexingJob:
    song_id: int
    generation: int
    attempts: int
    title: str
    main_artists: list[str]


class IndexingResult(BaseModel):
    indexed: bool = False
    retry_scheduled: bool = False


def claim_job() -> IndexingJob | None:
    connection = get_connection()
    try:
        rows = connection.run("""
            WITH candidate AS (
                SELECT song_id FROM song_embedding_queue
                WHERE available_at <= now()
                ORDER BY available_at, song_id
                FOR UPDATE SKIP LOCKED LIMIT 1
            ), claimed AS (
                UPDATE song_embedding_queue queue
                SET attempts = attempts + 1,
                    available_at = now() + :lease_seconds * interval '1 second'
                FROM candidate WHERE queue.song_id = candidate.song_id
                RETURNING queue.song_id, queue.generation, queue.attempts
            )
            SELECT claimed.song_id, claimed.generation, claimed.attempts, songs.title,
                COALESCE((SELECT array_agg(name ORDER BY display_order)
                    FROM song_artists WHERE song_id = songs.id AND role = 'MAIN'), '{}')
            FROM claimed JOIN songs ON songs.id = claimed.song_id
            WHERE songs.verification_status = :verified_status
        """, lease_seconds=LEASE_SECONDS, verified_status=VERIFICATION_STATUS_VERIFIED)
        if not rows:
            return None
        (claimed_row,) = rows
        song_id, generation, attempts, title, main_artists = claimed_row
        return IndexingJob(song_id, generation, attempts, title, main_artists)
    finally:
        connection.close()


def complete_job(job: IndexingJob, embedding: list[float]) -> bool:
    connection = get_connection()
    try:
        # Song-first locking matches metadata updates and their queue triggers.
        connection.run("BEGIN")
        songs = connection.run("SELECT id FROM songs WHERE id = :song_id FOR UPDATE", song_id=job.song_id)
        if not songs:
            connection.run("ROLLBACK")
            return False
        rows = connection.run("""
            DELETE FROM song_embedding_queue WHERE song_id = :song_id
                AND generation = :generation AND attempts = :attempts RETURNING song_id
        """, song_id=job.song_id, generation=job.generation, attempts=job.attempts)
        if rows:
            connection.run("""
                UPDATE songs SET embedding = :embedding
                WHERE id = :song_id AND verification_status = :verified_status
            """, song_id=job.song_id, embedding=Vector(embedding), verified_status=VERIFICATION_STATUS_VERIFIED)
        connection.run("COMMIT")
        return bool(rows)
    except Exception:
        connection.run("ROLLBACK")
        raise
    finally:
        connection.close()


def retry_job(job: IndexingJob) -> None:
    retry_seconds = min(RETRY_MAX_SECONDS, RETRY_BASE_SECONDS * 2 ** min(job.attempts, RETRY_MAX_EXPONENT))
    connection = get_connection()
    try:
        connection.run("""
            UPDATE song_embedding_queue
            SET available_at = now() + :retry_seconds * interval '1 second'
            WHERE song_id = :song_id AND generation = :generation AND attempts = :attempts
        """, song_id=job.song_id, generation=job.generation, attempts=job.attempts, retry_seconds=retry_seconds)
    finally:
        connection.close()


def index_next_song() -> IndexingResult:
    job = claim_job()
    if job is None:
        return IndexingResult()
    try:
        if not job.main_artists:
            raise ValueError("A verified song must have a main artist")
        normalized_text = normalize_artist_and_title(" ".join(job.main_artists), job.title)
        embedding = generate_embedding(normalized_text)
        if len(embedding) != EMBEDDING_DIMENSIONS:
            raise ValueError("Embedding dimensions do not match the catalog")
        return IndexingResult(indexed=complete_job(job, embedding))
    except Exception as failure:
        retry_job(job)
        logger.warning("Song embedding indexing postponed for song %s: %s", job.song_id, type(failure).__name__)
        return IndexingResult(retry_scheduled=True)
