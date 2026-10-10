from app.dedup import indexing

SONG_ID = 42
GENERATION = 7
FIRST_ATTEMPT = 1
EXPECTED_RETRIES = 2
EMBEDDING = [0.25] * indexing.EMBEDDING_DIMENSIONS


def test_indexing_uses_normalized_verified_metadata(mocker):
    job = indexing.IndexingJob(SONG_ID, GENERATION, FIRST_ATTEMPT, "Élan!", ["Björk", "Guest"])
    mocker.patch.object(indexing, "claim_job", return_value=job)
    generate = mocker.patch.object(indexing, "generate_embedding", return_value=EMBEDDING)
    complete = mocker.patch.object(indexing, "complete_job", return_value=True)
    retry = mocker.patch.object(indexing, "retry_job")
    assert indexing.index_next_song().indexed
    generate.assert_called_once_with("bjork guest elan")
    complete.assert_called_once_with(job, EMBEDDING)
    retry.assert_not_called()


def test_empty_queue_does_not_call_openai(mocker):
    mocker.patch.object(indexing, "claim_job", return_value=None)
    generate = mocker.patch.object(indexing, "generate_embedding")
    assert not indexing.index_next_song().indexed
    generate.assert_not_called()


def test_failed_embedding_is_retried_without_logging_exception_content(mocker, caplog):
    job = indexing.IndexingJob(SONG_ID, GENERATION, FIRST_ATTEMPT, "Title", ["Artist"])
    mocker.patch.object(indexing, "claim_job", return_value=job)
    mocker.patch.object(indexing, "generate_embedding", side_effect=RuntimeError("private request content"))
    complete = mocker.patch.object(indexing, "complete_job")
    retry = mocker.patch.object(indexing, "retry_job")
    assert indexing.index_next_song().retry_scheduled
    retry.assert_called_once_with(job)
    complete.assert_not_called()
    assert "private request content" not in caplog.text


def test_missing_artist_or_wrong_dimensions_preserves_retryable_work(mocker):
    job = indexing.IndexingJob(SONG_ID, GENERATION, FIRST_ATTEMPT, "Title", [])
    mocker.patch.object(indexing, "claim_job", return_value=job)
    generate = mocker.patch.object(indexing, "generate_embedding", return_value=[])
    retry = mocker.patch.object(indexing, "retry_job")
    assert indexing.index_next_song().retry_scheduled
    generate.assert_not_called()
    populated_job = indexing.IndexingJob(SONG_ID, GENERATION, FIRST_ATTEMPT, "Title", ["Artist"])
    mocker.patch.object(indexing, "claim_job", return_value=populated_job)
    assert indexing.index_next_song().retry_scheduled
    assert retry.call_count == EXPECTED_RETRIES


def test_stale_completion_does_not_report_success(mocker):
    job = indexing.IndexingJob(SONG_ID, GENERATION, FIRST_ATTEMPT, "Title", ["Artist"])
    mocker.patch.object(indexing, "claim_job", return_value=job)
    mocker.patch.object(indexing, "generate_embedding", return_value=EMBEDDING)
    mocker.patch.object(indexing, "complete_job", return_value=False)
    result = indexing.index_next_song()
    assert not result.indexed
    assert not result.retry_scheduled
