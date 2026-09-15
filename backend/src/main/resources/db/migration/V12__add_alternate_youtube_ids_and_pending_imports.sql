-- Alternate YouTube IDs: many alternate uploads of a known track map to one canonical
-- song, so a re-upload does not create a duplicate song or re-run the metadata pipeline.
-- youtube_id is unique so the same alternate upload is never linked twice.
CREATE TABLE alternate_youtube_ids (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    youtube_id VARCHAR(255) NOT NULL UNIQUE,
    song_id BIGINT NOT NULL REFERENCES songs (id)
);

-- The admin catalog-seeding backlog: YouTube IDs submitted for eventual scheduled
-- processing, each moving through pending, processing, done, or failed.
CREATE TABLE pending_imports (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    youtube_id VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    enqueued_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    failure_reason VARCHAR(255)
);

-- The scheduled drain reads pending rows oldest first; the backlog-status view counts
-- pending rows and rows completed since the start of the current day.
CREATE INDEX idx_pending_imports_status_enqueued_at ON pending_imports (status, enqueued_at);
CREATE INDEX idx_pending_imports_status_processed_at ON pending_imports (status, processed_at);
