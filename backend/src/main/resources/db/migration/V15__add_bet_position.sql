-- Records where the bettor staked their token on their own timeline, alongside the
-- existing bettor_player_id and bet_placed_at columns. Set atomically with those two by
-- RoundRepository.tryAcceptBet.

ALTER TABLE rounds ADD COLUMN bet_position INTEGER;
