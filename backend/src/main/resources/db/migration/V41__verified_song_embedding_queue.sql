CREATE SEQUENCE song_embedding_generation;

CREATE TABLE song_embedding_queue (
    song_id BIGINT PRIMARY KEY REFERENCES songs(id) ON DELETE CASCADE,
    generation BIGINT NOT NULL DEFAULT nextval('song_embedding_generation'),
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX song_embedding_queue_available ON song_embedding_queue(available_at);

CREATE FUNCTION schedule_song_embedding(target_song_id BIGINT) RETURNS VOID AS $$
BEGIN
    UPDATE songs SET embedding = NULL WHERE id = target_song_id;
    IF EXISTS (SELECT FROM songs WHERE id = target_song_id AND verification_status = 'VERIFIED') THEN
        INSERT INTO song_embedding_queue(song_id) VALUES (target_song_id)
        ON CONFLICT(song_id) DO UPDATE SET generation = nextval('song_embedding_generation'),
            attempts = 0, available_at = now();
    ELSE
        DELETE FROM song_embedding_queue WHERE song_id = target_song_id;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION song_embedding_metadata_changed() RETURNS TRIGGER AS $$
BEGIN
    PERFORM schedule_song_embedding(NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER song_embedding_insert AFTER INSERT ON songs
FOR EACH ROW EXECUTE FUNCTION song_embedding_metadata_changed();
CREATE TRIGGER song_embedding_metadata_update AFTER UPDATE OF title, verification_status ON songs
FOR EACH ROW WHEN (OLD.title IS DISTINCT FROM NEW.title
    OR OLD.verification_status IS DISTINCT FROM NEW.verification_status)
EXECUTE FUNCTION song_embedding_metadata_changed();

CREATE FUNCTION song_embedding_artists_changed() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM schedule_song_embedding(OLD.song_id);
        RETURN OLD;
    END IF;
    PERFORM schedule_song_embedding(NEW.song_id);
    IF TG_OP = 'UPDATE' AND OLD.song_id IS DISTINCT FROM NEW.song_id THEN
        PERFORM schedule_song_embedding(OLD.song_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER song_embedding_artist_update AFTER INSERT OR UPDATE OR DELETE ON song_artists
FOR EACH ROW EXECUTE FUNCTION song_embedding_artists_changed();

INSERT INTO song_embedding_queue(song_id)
SELECT id FROM songs WHERE verification_status = 'VERIFIED' AND embedding IS NULL;
