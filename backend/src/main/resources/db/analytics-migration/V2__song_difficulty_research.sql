CREATE TABLE song_play_observations (
    event_id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    song_id BIGINT NOT NULL,
    research_player_id UUID,
    game_correlation_id UUID NOT NULL,
    placement_outcome VARCHAR(24) NOT NULL,
    timeline_card_count INTEGER NOT NULL,
    valid_insertion_slot_count INTEGER NOT NULL,
    title_attempted BOOLEAN NOT NULL,
    title_correct BOOLEAN NOT NULL,
    artist_attempts INTEGER NOT NULL,
    correct_artists INTEGER NOT NULL,
    requested_difficulty_tier VARCHAR(16),
    rules_version VARCHAR(32) NOT NULL
);
CREATE INDEX song_observation_expiry ON song_play_observations(occurred_at);
CREATE INDEX song_observation_player ON song_play_observations(research_player_id);
CREATE TABLE song_difficulty_aggregates (
    song_id BIGINT NOT NULL,
    rules_version VARCHAR(32) NOT NULL,
    timeline_size_band INTEGER NOT NULL,
    placement_attempts BIGINT NOT NULL,
    correct_placements BIGINT NOT NULL,
    title_attempts BIGINT NOT NULL,
    correct_titles BIGINT NOT NULL,
    artist_attempts BIGINT NOT NULL,
    correct_artists BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(song_id, rules_version, timeline_size_band)
);
CREATE TABLE research_processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE research_deleted_players (
    research_player_id UUID PRIMARY KEY
);
