-- Ephemeral game-session schema for story 10. Every row here is purged once a session
-- ends or is abandoned (see GameSessionService.purgeSession); nothing here is meant to
-- outlive a single playthrough. A results export is generated before the purge runs on
-- a normal completion and is handed back over the wire, not persisted to its own table.

CREATE TABLE game_sessions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id BIGINT NOT NULL UNIQUE REFERENCES groups (id),
    status VARCHAR(255) NOT NULL,
    dj_mode VARCHAR(255) NOT NULL,
    win_condition_card_count INTEGER NOT NULL,
    current_round_number INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    zero_connected_since TIMESTAMP,
    fixed_dj_player_id BIGINT
);

-- Remaining song ids for future rounds, in play order. Populated once at session start
-- and popped from the front as each round begins; never replenished.
CREATE TABLE game_session_song_queue (
    session_id BIGINT NOT NULL REFERENCES game_sessions (id),
    queue_position INTEGER NOT NULL,
    song_id BIGINT NOT NULL,
    PRIMARY KEY (session_id, queue_position)
);

CREATE TABLE players (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES game_sessions (id),
    user_id BIGINT NOT NULL REFERENCES users (id),
    display_name VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(255),
    turn_order INTEGER NOT NULL,
    token_count INTEGER NOT NULL,
    status VARCHAR(255) NOT NULL,
    is_connected BOOLEAN NOT NULL,
    disconnected_at TIMESTAMP,
    total_artists_guessed INTEGER NOT NULL,
    total_titles_guessed INTEGER NOT NULL
);

CREATE TABLE player_cards (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_id BIGINT NOT NULL REFERENCES players (id),
    song_id BIGINT NOT NULL REFERENCES songs (id),
    release_year INTEGER NOT NULL,
    position INTEGER NOT NULL
);

CREATE TABLE rounds (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES game_sessions (id),
    round_number INTEGER NOT NULL,
    active_player_id BIGINT NOT NULL REFERENCES players (id),
    dj_player_id BIGINT NOT NULL REFERENCES players (id),
    song_id BIGINT NOT NULL REFERENCES songs (id),
    status VARCHAR(255) NOT NULL,
    placed_position INTEGER,
    placement_correct BOOLEAN,
    locked_in_at TIMESTAMP,
    betting_window_ends_at TIMESTAMP,
    -- The single accepted bet on this round, if any. Claimed through one atomic
    -- conditional UPDATE (see RoundRepository.tryAcceptBet), which is what makes betting
    -- concurrency-safe: the first transaction to hit this row wins, everyone else's
    -- update affects zero rows.
    bettor_player_id BIGINT REFERENCES players (id),
    bet_placed_at TIMESTAMP,
    revealed_at TIMESTAMP,
    scored_at TIMESTAMP
);

CREATE TABLE guesses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    round_id BIGINT NOT NULL REFERENCES rounds (id),
    player_id BIGINT NOT NULL REFERENCES players (id),
    guessed_artist VARCHAR(255),
    guessed_title VARCHAR(255),
    is_artist_correct BOOLEAN NOT NULL,
    is_title_correct BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL
);
