from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import pytest

from app.dedup import indexing
from app.dedup.database import get_connection
from app.dedup.repository import find_best_verified_match
from app.metadata import service

EMBEDDING = [0.25] * indexing.EMBEDDING_DIMENSIONS
VERIFIED = "VERIFIED"
UNVERIFIED = "UNVERIFIED"
CLAIM_WORKERS = 2


def execute(query, **parameters):
    connection = get_connection()
    try:
        return connection.run(query, **parameters)
    finally:
        connection.close()


def song(status=VERIFIED):
    rows = execute("""
        INSERT INTO songs(title, release_year, verification_status, confidence)
        VALUES ('One More Time', 2000, :status, 'high') RETURNING id
    """, status=status)
    (inserted_row,) = rows
    (song_id,) = inserted_row
    execute("""INSERT INTO song_artists(song_id, name, role, display_order)
        VALUES (:song_id, 'Daft Punk', 'MAIN', 0)""", song_id=song_id)
    return song_id


@pytest.fixture
def migrated_queue(postgres_connection_url):
    repository_root = next(parent for parent in Path(__file__).resolve().parents if (parent / "backend").is_dir())
    migration = repository_root / "backend/src/main/resources/db/migration/V41__verified_song_embedding_queue.sql"
    execute(migration.read_text(encoding="utf-8"))
    try:
        yield
    finally:
        execute("""
            DROP FUNCTION song_embedding_metadata_changed() CASCADE;
            DROP FUNCTION song_embedding_artists_changed() CASCADE;
            DROP FUNCTION schedule_song_embedding(BIGINT);
            DROP TABLE song_embedding_queue;
            DROP SEQUENCE song_embedding_generation;
        """)


def test_migration_backfills_only_verified_missing_embeddings(postgres_connection_url):
    verified_song_id = song()
    song(UNVERIFIED)
    repository_root = next(parent for parent in Path(__file__).resolve().parents if (parent / "backend").is_dir())
    migration = repository_root / "backend/src/main/resources/db/migration/V41__verified_song_embedding_queue.sql"
    execute(migration.read_text(encoding="utf-8"))
    try:
        assert execute("SELECT song_id FROM song_embedding_queue") == [[verified_song_id]]
    finally:
        execute("""
            DROP FUNCTION song_embedding_metadata_changed() CASCADE;
            DROP FUNCTION song_embedding_artists_changed() CASCADE;
            DROP FUNCTION schedule_song_embedding(BIGINT);
            DROP TABLE song_embedding_queue;
            DROP SEQUENCE song_embedding_generation;
        """)


def test_new_verified_song_is_indexed_and_alternate_upload_skips_pipeline(migrated_queue, mocker):
    song_id = song()
    assert find_best_verified_match(EMBEDDING) is None
    mocker.patch.object(indexing, "generate_embedding", return_value=EMBEDDING)
    assert indexing.index_next_song().indexed
    assert find_best_verified_match(EMBEDDING).id == song_id
    assert execute("SELECT title, release_year FROM songs WHERE id = :song_id", song_id=song_id) == [["One More Time", 2000]]
    mocker.patch.object(service.youtube, "fetch_youtube_metadata", return_value={
        "video_title": "One More Time (Official Video)", "channel_title": "Daft Punk",
        "upload_year": 2019, "description": "",
    })
    mocker.patch.object(service, "generate_embedding", return_value=EMBEDDING)
    precheck = mocker.patch.object(service, "_run_precheck")
    gather = mocker.patch.object(service.musicbrainz, "search")
    result = service.resolve_metadata("https://youtube.com/watch?v=abc12345678")
    assert result.content.canonical_song_id == song_id
    precheck.assert_not_called()
    gather.assert_not_called()


def test_upgrade_and_artist_edit_invalidate_embeddings(migrated_queue, mocker):
    song_id = song(UNVERIFIED)
    assert indexing.claim_job() is None
    execute("UPDATE songs SET verification_status = :status WHERE id = :song_id", status=VERIFIED, song_id=song_id)
    mocker.patch.object(indexing, "generate_embedding", return_value=EMBEDDING)
    assert indexing.index_next_song().indexed
    execute("UPDATE song_artists SET name = 'Changed Artist' WHERE song_id = :song_id", song_id=song_id)
    assert find_best_verified_match(EMBEDDING) is None
    job = indexing.claim_job()
    assert job.main_artists == ["Changed Artist"]
    execute("UPDATE songs SET verification_status = :status WHERE id = :song_id", status=UNVERIFIED, song_id=song_id)
    assert not indexing.complete_job(job, EMBEDDING)
    assert indexing.claim_job() is None


def test_old_metadata_result_cannot_clear_new_work(migrated_queue):
    song_id = song()
    old_job = indexing.claim_job()
    execute("UPDATE songs SET title = 'New Title' WHERE id = :song_id", song_id=song_id)
    assert not indexing.complete_job(old_job, EMBEDDING)
    new_job = indexing.claim_job()
    assert new_job.title == "New Title"
    assert new_job.generation != old_job.generation
    indexing.retry_job(old_job)
    assert indexing.complete_job(new_job, EMBEDDING)


def test_lease_prevents_concurrent_claims_and_recovers_crashes(migrated_queue):
    song()
    with ThreadPoolExecutor(max_workers=CLAIM_WORKERS) as workers:
        results = list(workers.map(lambda unused: indexing.claim_job(), range(CLAIM_WORKERS)))
    (claimed_job,) = [result for result in results if result is not None]
    assert indexing.claim_job() is None
    execute("UPDATE song_embedding_queue SET available_at = now() - interval '1 second'")
    recovered_job = indexing.claim_job()
    assert recovered_job.attempts > claimed_job.attempts
    assert not indexing.complete_job(claimed_job, EMBEDDING)
    assert indexing.complete_job(recovered_job, EMBEDDING)


def test_failure_retries_later_and_eventually_indexes(migrated_queue, mocker):
    song()
    embedding = mocker.patch.object(indexing, "generate_embedding", side_effect=RuntimeError("unavailable"))
    assert indexing.index_next_song().retry_scheduled
    assert indexing.claim_job() is None
    execute("UPDATE song_embedding_queue SET available_at = now() - interval '1 second'")
    embedding.side_effect = None
    embedding.return_value = EMBEDDING
    assert indexing.index_next_song().indexed
    assert execute("SELECT song_id FROM song_embedding_queue") == []
