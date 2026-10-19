-- When a song entered the catalog. Existing songs take the oldest backlog row for their
-- YouTube ID; song_playlists has no timestamp, so a song with no backlog row stays NULL
-- (unknown). pending_imports.enqueued_at holds UTC wall time, as the application reads it.
ALTER TABLE songs ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;

UPDATE songs
SET created_at = earliest_backlog_row.first_enqueued_at
FROM (
    SELECT youtube_id, min(enqueued_at) AT TIME ZONE 'UTC' AS first_enqueued_at
    FROM pending_imports
    GROUP BY youtube_id
) earliest_backlog_row
WHERE earliest_backlog_row.youtube_id = songs.youtube_id;

-- Set after the backfill so existing rows are not stamped with the migration time.
ALTER TABLE songs ALTER COLUMN created_at SET DEFAULT now();

CREATE INDEX songs_created_at_idx ON songs (created_at);

-- The admin song catalog searches title and artist name by case-insensitive substring,
-- matches an exact YouTube ID, and reads each song's backlog rows by YouTube ID.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX songs_title_trgm_idx ON songs USING gin (title gin_trgm_ops);
CREATE INDEX song_artists_name_trgm_idx ON song_artists USING gin (name gin_trgm_ops);
CREATE INDEX songs_youtube_id_idx ON songs (youtube_id);
CREATE INDEX pending_imports_youtube_id_idx ON pending_imports (youtube_id);
