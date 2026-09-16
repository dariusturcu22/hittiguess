-- Betting targets one shared timeline per round, the active player's own, not a separate
-- timeline per bettor. Story 10's original bettor_player_id/bet_placed_at/bet_position
-- columns on rounds modeled one bet per round; they're replaced by a bets table with one
-- row per accepted bet, so a round can carry several bets at once, one per distinct gap.
-- The two unique constraints below are what makes accepting a bet concurrency-safe: a
-- plain INSERT relies on Postgres rejecting a genuine conflict (same gap, or the same
-- player twice) as an integrity violation, rather than checking-then-inserting in
-- application code. See BetRepository.insertBet and DECISIONS.md.

ALTER TABLE rounds DROP COLUMN bettor_player_id;
ALTER TABLE rounds DROP COLUMN bet_placed_at;
ALTER TABLE rounds DROP COLUMN bet_position;

CREATE TABLE bets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    round_id BIGINT NOT NULL REFERENCES rounds (id),
    player_id BIGINT NOT NULL REFERENCES players (id),
    position INTEGER NOT NULL,
    placed_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_bets_round_position UNIQUE (round_id, position),
    CONSTRAINT uq_bets_round_player UNIQUE (round_id, player_id)
);
