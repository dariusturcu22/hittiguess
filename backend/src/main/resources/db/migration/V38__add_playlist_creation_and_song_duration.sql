ALTER TABLE playlists ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE songs ADD COLUMN duration_seconds INTEGER;
ALTER TABLE songs ADD COLUMN duration_fetched_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE songs ADD CONSTRAINT songs_duration_positive CHECK (duration_seconds > 0);
