CREATE TABLE analytics_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload JSONB NOT NULL
);

CREATE INDEX analytics_events_occurred_at_idx ON analytics_events (occurred_at);
CREATE INDEX analytics_events_event_type_idx ON analytics_events (event_type);
