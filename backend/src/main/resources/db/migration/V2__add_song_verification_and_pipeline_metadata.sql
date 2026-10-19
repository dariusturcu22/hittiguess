ALTER TABLE songs
    ADD COLUMN verification_status VARCHAR(255) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN confidence VARCHAR(255),
    ADD COLUMN metadata_raw TEXT;
