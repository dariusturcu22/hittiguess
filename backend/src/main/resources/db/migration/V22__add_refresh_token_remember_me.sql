-- Remember-me flag on refresh tokens (landing and auth fix pass). Defaults to false
-- so every existing session keeps the standard lifetime on upgrade.
ALTER TABLE refresh_tokens ADD COLUMN remember_me BOOLEAN NOT NULL DEFAULT FALSE;
