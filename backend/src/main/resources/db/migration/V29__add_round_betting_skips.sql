-- The players who chose to skip a round's betting window. The window closes early once
-- every eligible bettor holding a token has either bet or skipped.
CREATE TABLE round_betting_skips (
    round_id BIGINT NOT NULL REFERENCES rounds (id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL,
    PRIMARY KEY (round_id, player_id)
);
