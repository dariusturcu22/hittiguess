-- A round is a full pass through the players; each rounds row is one turn inside it.
ALTER TABLE rounds ADD COLUMN game_round_number INT NOT NULL DEFAULT 1;
ALTER TABLE game_sessions ADD COLUMN current_game_round_number INT NOT NULL DEFAULT 1;
