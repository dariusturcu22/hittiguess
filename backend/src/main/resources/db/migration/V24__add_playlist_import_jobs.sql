-- Playlist-scoped background imports: a user starts an import from a playlist page and
-- keeps browsing while it resolves. The job row tracks the run, its item rows track
-- each video id so the detail view can render pending songs greyed out.
CREATE TABLE playlist_import_jobs (
    id VARCHAR(36) PRIMARY KEY,
    playlist_id BIGINT NOT NULL REFERENCES playlists (id) ON DELETE CASCADE,
    submitted_by_username VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE TABLE playlist_import_job_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL REFERENCES playlist_import_jobs (id) ON DELETE CASCADE,
    youtube_id VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    song_id BIGINT
);

-- The detail view reads one playlist's running job with its items in one go.
CREATE INDEX idx_playlist_import_jobs_playlist_status ON playlist_import_jobs (playlist_id, status);
CREATE INDEX idx_playlist_import_job_items_job ON playlist_import_job_items (job_id);
