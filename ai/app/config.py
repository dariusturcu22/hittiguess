from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    openai_api_key: str
    youtube_api_key: str
    discogs_consumer_key: str
    discogs_consumer_secret: str
    internal_service_api_key: str
    openai_model: str = "gpt-5.1"
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

    # Distributed tracing (OpenTelemetry): spans are always created in-process so
    # request logs can be correlated with a trace id, but nothing is exported over
    # OTLP until this is pointed at a real collector or Grafana Cloud Tempo instance.
    otel_exporter_otlp_endpoint: str | None = None


settings = Settings()
