CREATE TABLE api_usage_daily (
    usage_date DATE NOT NULL,
    provider TEXT NOT NULL,
    operation TEXT NOT NULL,
    call_count BIGINT NOT NULL DEFAULT 0,
    quota_units BIGINT NOT NULL DEFAULT 0,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT api_usage_daily_day_provider_operation UNIQUE (usage_date, provider, operation)
);
