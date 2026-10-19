-- Story 50: TOTP-based two-factor authentication. totp_secret holds the pending secret
-- from /auth/2fa/setup before two_factor_enabled flips true on /auth/2fa/confirm, and the
-- confirmed secret afterward; /auth/2fa/disable clears both. See DECISIONS.md for why the
-- column is plain text rather than encrypted at rest.

ALTER TABLE users ADD COLUMN totp_secret VARCHAR(255);
ALTER TABLE users ADD COLUMN two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE two_factor_backup_codes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    code_hash VARCHAR(255) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_two_factor_backup_codes_user_id ON two_factor_backup_codes (user_id);
