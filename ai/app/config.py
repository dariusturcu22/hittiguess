from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    openai_api_key: str
    youtube_api_key: str
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


settings = Settings()
