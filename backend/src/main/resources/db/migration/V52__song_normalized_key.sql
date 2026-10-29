-- The first main artist and title as GameSongList normalizes them, so a finished pipeline run
-- finds an existing copy of the same song by index. Existing rows start empty and
-- SongNormalizedKeyBackfill fills them on startup; not unique, because duplicate pairs exist
-- until an admin merges them.
ALTER TABLE songs ADD COLUMN normalized_key VARCHAR(1024);
CREATE INDEX songs_normalized_key_idx ON songs (normalized_key);
