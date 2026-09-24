-- When the active player's time to place the round's card runs out. An idle active
-- player no longer holds the round forever: at this instant the card is discarded and
-- the game moves on. Null for rounds created before this column existed.
ALTER TABLE rounds ADD COLUMN placement_ends_at TIMESTAMP;
