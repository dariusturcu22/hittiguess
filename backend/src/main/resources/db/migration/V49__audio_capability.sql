-- Whether a member's browser can capture audio, so the DJ rotation can skip players who
-- cannot share a song. Existing rows keep the old behavior of being eligible.
ALTER TABLE members ADD COLUMN can_share_audio BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE players ADD COLUMN can_dj BOOLEAN NOT NULL DEFAULT TRUE;
