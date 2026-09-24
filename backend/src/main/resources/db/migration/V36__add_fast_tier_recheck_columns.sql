-- A backlog row is an admin seed or the patient recheck of a provisional answer a user
-- path already saved (a fast-tier import, a manual add); a recheck keeps the
-- provisional year next to the patient tier's.
ALTER TABLE pending_imports ADD COLUMN origin VARCHAR(32) NOT NULL DEFAULT 'ADMIN_SEED';
ALTER TABLE pending_imports ADD COLUMN provisional_year INT;
ALTER TABLE pending_imports ADD COLUMN patient_year INT;
CREATE INDEX idx_pending_imports_origin_enqueued ON pending_imports (origin, enqueued_at);
