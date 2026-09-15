-- Community song reports and confirmations (story 17). A report flags a song's metadata as
-- wrong, a confirmation is the positive thumbs-up that it is right. Both tie a user to a song,
-- with a unique constraint so a given user cannot report or confirm the same song twice.
-- Resolution stays manual: an admin moves a report to upheld or dismissed, nothing here
-- changes a song's verification_status on its own.
CREATE TABLE song_reports (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id BIGINT NOT NULL REFERENCES users (id),
    song_id BIGINT NOT NULL REFERENCES songs (id),
    message TEXT NOT NULL,
    suggested_correct_year INTEGER,
    sources TEXT,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_song_report_reporter_song UNIQUE (reporter_id, song_id)
);

CREATE TABLE song_confirmations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    song_id BIGINT NOT NULL REFERENCES songs (id),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_song_confirmation_user_song UNIQUE (user_id, song_id)
);

-- The admin review queue reads open reports and confirmations grouped by song, so both
-- foreign keys carry an index for the per-song aggregation.
CREATE INDEX idx_song_reports_song_id ON song_reports (song_id);
CREATE INDEX idx_song_reports_status ON song_reports (status);
CREATE INDEX idx_song_confirmations_song_id ON song_confirmations (song_id);
