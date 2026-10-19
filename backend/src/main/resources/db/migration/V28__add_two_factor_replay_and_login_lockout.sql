-- The last TOTP time step a user's code was accepted for, so the same code can't be
-- replayed within its validity window. Null until the first accepted code.
ALTER TABLE users ADD COLUMN totp_last_used_step BIGINT;

-- Consecutive failed password or two-factor attempts, and the time the account stays
-- locked until once they reach the limit. Reset on the next successful login.
ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN login_locked_until TIMESTAMP;
