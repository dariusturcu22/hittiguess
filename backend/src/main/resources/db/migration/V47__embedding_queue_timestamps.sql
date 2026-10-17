-- Records when the V41 embedding executor claims a queue row and when a song's embedding is
-- stored, so the admin queue view can tell claimed work from retry backoff and report
-- completions and durations. Triggers keep the AI service's queue SQL unchanged.
ALTER TABLE song_embedding_queue ADD COLUMN claimed_at TIMESTAMPTZ;
ALTER TABLE songs ADD COLUMN embedding_started_at TIMESTAMPTZ;
ALTER TABLE songs ADD COLUMN embedding_stored_at TIMESTAMPTZ;

-- A claim raises attempts; a retry only moves available_at; a reschedule resets attempts and
-- the generation. Only a claim leaves claimed_at set.
CREATE FUNCTION song_embedding_queue_track_claim() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.attempts > OLD.attempts THEN
        NEW.claimed_at := now();
    ELSIF NEW.attempts < OLD.attempts OR NEW.generation <> OLD.generation
        OR NEW.available_at IS DISTINCT FROM OLD.available_at THEN
        NEW.claimed_at := NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER song_embedding_queue_claim BEFORE UPDATE ON song_embedding_queue
FOR EACH ROW EXECUTE FUNCTION song_embedding_queue_track_claim();

-- Completion deletes the queue row before storing the embedding in the same transaction, so the
-- claim time is handed to the song update through a transaction-local setting.
CREATE FUNCTION song_embedding_queue_remember_claim() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.claimed_at IS NOT NULL THEN
        PERFORM set_config('hittiguess.embedding_claim', OLD.song_id || '@' || OLD.claimed_at::text, true);
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER song_embedding_queue_completion BEFORE DELETE ON song_embedding_queue
FOR EACH ROW EXECUTE FUNCTION song_embedding_queue_remember_claim();

CREATE FUNCTION song_embedding_track_storage() RETURNS TRIGGER AS $$
DECLARE
    remembered_claim TEXT := current_setting('hittiguess.embedding_claim', true);
BEGIN
    IF NEW.embedding IS NULL THEN
        NEW.embedding_started_at := NULL;
        NEW.embedding_stored_at := NULL;
    ELSIF OLD.embedding IS NULL OR NEW.embedding IS DISTINCT FROM OLD.embedding THEN
        NEW.embedding_stored_at := now();
        NEW.embedding_started_at := CASE WHEN split_part(remembered_claim, '@', 1) = NEW.id::text
            THEN split_part(remembered_claim, '@', 2)::timestamptz END;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER song_embedding_storage BEFORE UPDATE OF embedding ON songs
FOR EACH ROW EXECUTE FUNCTION song_embedding_track_storage();

CREATE INDEX songs_embedding_stored_at ON songs (embedding_stored_at) WHERE embedding_stored_at IS NOT NULL;
CREATE INDEX pipeline_jobs_done_finished_at ON pipeline_jobs (stage, finished_at) WHERE status = 'DONE';
