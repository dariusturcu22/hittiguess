-- Staged song ids for a group's upcoming game session, chosen during start with songs
-- or custom start and preserved across backend restarts during the pre-game audio check.
-- Cleared when the game starts or when the audio check is cancelled.
CREATE TABLE group_staged_songs (
    group_id BIGINT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    stage_position INTEGER NOT NULL,
    song_id BIGINT NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, stage_position)
);
