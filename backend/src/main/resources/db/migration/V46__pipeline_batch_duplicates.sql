ALTER TABLE import_batches ADD COLUMN skipped_duplicates INTEGER NOT NULL DEFAULT 0;
ALTER TABLE import_batches ADD CONSTRAINT import_batches_skipped_duplicates_not_negative CHECK (skipped_duplicates >= 0);
