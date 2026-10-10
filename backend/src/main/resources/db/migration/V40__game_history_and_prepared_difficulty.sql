ALTER TABLE groups ADD COLUMN difficulty_tier VARCHAR(16);
ALTER TABLE game_sessions ADD COLUMN difficulty_tier VARCHAR(16);
ALTER TABLE rounds ADD COLUMN timeline_card_count INTEGER;
ALTER TABLE rounds ADD COLUMN valid_insertion_slot_count INTEGER;
ALTER TABLE bets ADD COLUMN won BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE game_summaries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_session_id BIGINT NOT NULL UNIQUE,
    group_name TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL,
    mode VARCHAR(32) NOT NULL,
    difficulty_tier VARCHAR(16),
    win_target_cards INTEGER NOT NULL,
    participant_count INTEGER NOT NULL,
    turns_played INTEGER NOT NULL,
    ending_reason VARCHAR(32) NOT NULL,
    rules_version VARCHAR(32) NOT NULL
);
CREATE TABLE game_participant_summaries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    game_summary_id BIGINT NOT NULL REFERENCES game_summaries(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    display_name TEXT NOT NULL,
    participation_status VARCHAR(16) NOT NULL,
    final_card_count INTEGER NOT NULL,
    card_rank INTEGER NOT NULL,
    artist_rank INTEGER NOT NULL,
    title_rank INTEGER NOT NULL,
    is_winner BOOLEAN NOT NULL,
    placement_attempts INTEGER NOT NULL,
    correct_placements INTEGER NOT NULL,
    title_attempts INTEGER NOT NULL,
    correct_titles INTEGER NOT NULL,
    artist_attempts INTEGER NOT NULL,
    correct_artists INTEGER NOT NULL,
    bets_placed INTEGER NOT NULL,
    bets_won INTEGER NOT NULL,
    UNIQUE(game_summary_id, user_id)
);
CREATE INDEX game_participant_history ON game_participant_summaries(user_id, game_summary_id DESC);
CREATE INDEX game_history_ended ON game_summaries(ended_at DESC, id DESC);

CREATE TABLE song_difficulty (
    song_id BIGINT PRIMARY KEY REFERENCES songs(id) ON DELETE CASCADE,
    score DOUBLE PRECISION NOT NULL CHECK(score BETWEEN 0 AND 1),
    tier VARCHAR(16) NOT NULL,
    placement_sample_count BIGINT NOT NULL DEFAULT 0,
    calculation_version VARCHAR(32) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    sampling_key DOUBLE PRECISION NOT NULL DEFAULT random()
);
CREATE INDEX song_difficulty_selection ON song_difficulty(tier, sampling_key, song_id);

CREATE TABLE research_player_identities (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    research_player_id UUID NOT NULL UNIQUE
);
CREATE TABLE research_delivery_queue (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    payload JSONB NOT NULL,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX research_delivery_order ON research_delivery_queue(queued_at, event_id);

WITH cold_start_parameters AS (
    SELECT 0.5 AS neutral_score, 40.0 AS widely_known_sitelinks,
           0.34 AS easy_upper_bound, 0.67 AS medium_upper_bound
), catalog AS (
    SELECT song.id, parameters.easy_upper_bound, parameters.medium_upper_bound,
           CASE WHEN song.wikidata_sitelinks_count IS NULL THEN parameters.neutral_score
           ELSE 1.0 - LEAST(parameters.widely_known_sitelinks, GREATEST(0, song.wikidata_sitelinks_count))
                / parameters.widely_known_sitelinks END AS difficulty_score
    FROM songs song CROSS JOIN cold_start_parameters parameters WHERE song.verification_status = 'VERIFIED'
)
INSERT INTO song_difficulty(song_id, score, tier, calculation_version, calculated_at)
SELECT id, difficulty_score,
       CASE WHEN difficulty_score < easy_upper_bound THEN 'EASY'
            WHEN difficulty_score < medium_upper_bound THEN 'MEDIUM' ELSE 'HARD' END,
       'global-v1', CURRENT_TIMESTAMP FROM catalog;

CREATE FUNCTION prepare_song_cold_start() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    neutral_score CONSTANT DOUBLE PRECISION := 0.5;
    widely_known_sitelinks CONSTANT INTEGER := 40;
    easy_upper_bound CONSTANT DOUBLE PRECISION := 0.34;
    medium_upper_bound CONSTANT DOUBLE PRECISION := 0.67;
    difficulty_score DOUBLE PRECISION;
BEGIN
    IF NEW.verification_status <> 'VERIFIED' THEN
        RETURN NEW;
    END IF;
    difficulty_score := CASE WHEN NEW.wikidata_sitelinks_count IS NULL THEN neutral_score
        ELSE 1.0 - LEAST(widely_known_sitelinks, GREATEST(0, NEW.wikidata_sitelinks_count))::DOUBLE PRECISION / widely_known_sitelinks END;
    INSERT INTO song_difficulty(song_id, score, tier, calculation_version, calculated_at)
    VALUES (NEW.id, difficulty_score,
      CASE WHEN difficulty_score < easy_upper_bound THEN 'EASY'
           WHEN difficulty_score < medium_upper_bound THEN 'MEDIUM' ELSE 'HARD' END,
      'global-v1', CURRENT_TIMESTAMP)
    ON CONFLICT(song_id) DO UPDATE SET score = EXCLUDED.score, tier = EXCLUDED.tier, calculated_at = EXCLUDED.calculated_at
    WHERE song_difficulty.placement_sample_count = 0;
    RETURN NEW;
END;
$$;
CREATE TRIGGER song_cold_start AFTER INSERT OR UPDATE OF verification_status, wikidata_sitelinks_count
ON songs FOR EACH ROW EXECUTE FUNCTION prepare_song_cold_start();
