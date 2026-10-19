-- Story 50: email verification on signup and a real password-reset flow. Both token
-- tables hold at most one active row per user; a new verification or reset request
-- deletes the previous row before inserting the next one, the same replace-on-reissue
-- pattern refresh_tokens already uses, so the unique constraint on user_id never conflicts.

-- Existing accounts predate the verification requirement and already log in successfully
-- today; retroactively locking them out would be a regression, not hardening. Adding the
-- column with a TRUE default backfills every existing row as verified, then the default
-- is flipped to FALSE so every account created from this point on starts unverified.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT FALSE;

CREATE TABLE email_verification_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users (id),
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE password_reset_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users (id),
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
);
