CREATE INDEX songs_duration_refresh_due ON songs (duration_fetched_at, youtube_id)
WHERE duration_fetched_at IS NOT NULL;
