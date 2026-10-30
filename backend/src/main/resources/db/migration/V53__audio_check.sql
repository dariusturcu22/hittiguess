-- The pre-game audio check: each member's share status during the check, each player's
-- share status while a game runs, and the time a game paused waiting for a DJ's share.
-- Groups reuse their status column for the new AUDIO_CHECK value.
ALTER TABLE members ADD COLUMN audio_check_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SHARED';
ALTER TABLE players ADD COLUMN audio_status VARCHAR(32);
ALTER TABLE game_sessions ADD COLUMN paused_at TIMESTAMP;
