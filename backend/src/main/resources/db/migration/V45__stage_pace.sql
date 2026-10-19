-- Pace and cooldown state of each stage that calls an external source, shared by every AI
-- service process so the spacing between requests survives restarts. The AI service reserves
-- a slot by moving next_slot_at forward in one UPDATE; cooldown_until records the end of the
-- latest pause the source asked for. current_delay_ms is the spacing the source currently
-- uses and avg_response_ms an exponential moving average of its response times.
CREATE TABLE stage_pace (
    stage VARCHAR(32) PRIMARY KEY,
    next_slot_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    cooldown_until TIMESTAMPTZ,
    current_delay_ms INTEGER NOT NULL DEFAULT 0,
    avg_response_ms INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT stage_pace_stage_known CHECK (stage IN ('YOUTUBE_METADATA', 'MUSICBRAINZ', 'DISCOGS',
        'WIKIDATA', 'WIKIPEDIA')),
    CONSTRAINT stage_pace_delay_not_negative CHECK (current_delay_ms >= 0),
    CONSTRAINT stage_pace_response_time_not_negative CHECK (avg_response_ms IS NULL OR avg_response_ms >= 0)
);

INSERT INTO stage_pace (stage) VALUES
    ('YOUTUBE_METADATA'), ('MUSICBRAINZ'), ('DISCOGS'), ('WIKIDATA'), ('WIKIPEDIA');
