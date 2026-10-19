-- The most recent completed session's results per group, kept past the purge of the
-- session's own rows and across a restart so the results screen and export stay
-- available. A later completed session for the same group replaces the row.
CREATE TABLE session_results (
    group_id BIGINT PRIMARY KEY REFERENCES groups (id) ON DELETE CASCADE,
    results TEXT NOT NULL,
    stored_at TIMESTAMP NOT NULL
);
