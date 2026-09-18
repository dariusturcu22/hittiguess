from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    openai_api_key: str
    youtube_api_key: str
    discogs_consumer_key: str
    discogs_consumer_secret: str
    internal_service_api_key: str
    reconciliation_model: str = "gpt-5-nano"
    embedding_model: str = "text-embedding-3-small"

    deepinfra_api_key: str
    deepinfra_model: str = "deepseek-ai/DeepSeek-V4-Flash"

    # Direct connection to the core transactional Postgres+pgvector instance, separate
    # from the core service's own datasource. The AI microservice reads and writes
    # embeddings directly (story 16) but never alters schema, that stays the core
    # service's responsibility through Flyway.
    database_url: str

    # Optional: unlock each wiki's authenticated rate-limit tier (200
    # requests/minute versus 10/minute anonymous). A bot password is issued
    # per wiki, Wikidata's doesn't authenticate against Wikipedia.
    wikidata_bot_username: str | None = None
    wikidata_bot_password: str | None = None
    wikipedia_bot_username: str | None = None
    wikipedia_bot_password: str | None = None

    # Error tracking (Sentry): inactive until a real DSN is supplied, matching the
    # Sentry SDK's own no-op behavior when sentry_sdk.init() is never called.
    sentry_dsn: str | None = None
    sentry_traces_sample_rate: float = 0.1

    # OpenTelemetry (traces and logs): spans and log records are always created
    # in-process so they can be correlated by trace id, but nothing is exported
    # over OTLP until this is pointed at a real collector or Grafana Cloud's OTLP
    # gateway. otel_exporter_otlp_headers authenticates against it, in the
    # standard "Key=Value" OTLP header format; parsed with the same utility the
    # OpenTelemetry SDK itself uses, not read from the process environment,
    # since pydantic-settings loads .env into this object directly rather than
    # into the real environment.
    otel_exporter_otlp_endpoint: str | None = None
    otel_exporter_otlp_headers: str | None = None


settings = Settings()
