import time

import pg8000.native
import pytest
from testcontainers.core.container import DockerContainer

from app.dedup.database import _parse_database_url

POSTGRES_IMAGE = "pgvector/pgvector:pg18"
POSTGRES_PORT = 5432
POSTGRES_DB = "hittiguess_test"
POSTGRES_USER = "hittiguess_test"
POSTGRES_PASSWORD = "hittiguess_test"

CONNECTION_RETRY_ATTEMPTS = 60
CONNECTION_RETRY_DELAY_SECONDS = 1.0

# Mirrors only the columns story 16's dedup queries touch, not the full backend
# schema: the AI microservice never runs Flyway, it only reads and writes rows
# against whatever schema the core service has already migrated in.
CREATE_SCHEMA_STATEMENTS = (
    "CREATE EXTENSION IF NOT EXISTS vector",
    """
    CREATE TABLE songs (
        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        title VARCHAR(255) NOT NULL,
        release_year INTEGER NOT NULL,
        gradient_color1 VARCHAR(255),
        gradient_color2 VARCHAR(255),
        verification_status VARCHAR(255) NOT NULL DEFAULT 'UNVERIFIED',
        confidence VARCHAR(255),
        embedding vector(1536)
    )
    """,
    """
    CREATE TABLE song_artists (
        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        song_id BIGINT NOT NULL REFERENCES songs (id),
        name VARCHAR(255) NOT NULL,
        role VARCHAR(255) NOT NULL,
        display_order INTEGER NOT NULL
    )
    """,
)


def _build_connection_url(host: str, port: str) -> str:
    return f"postgresql://{POSTGRES_USER}:{POSTGRES_PASSWORD}@{host}:{port}/{POSTGRES_DB}"


def _wait_until_connectable(connection_url: str) -> None:
    last_connection_error: Exception | None = None
    for _ in range(CONNECTION_RETRY_ATTEMPTS):
        try:
            connection = pg8000.native.Connection(**_parse_database_url(connection_url))
            connection.close()
            return
        except Exception as connection_error:
            last_connection_error = connection_error
            time.sleep(CONNECTION_RETRY_DELAY_SECONDS)
    raise RuntimeError(f"Postgres test container never became connectable: {last_connection_error}")


@pytest.fixture(scope="session")
def postgres_connection_url():
    container = (
        DockerContainer(POSTGRES_IMAGE)
        .with_env("POSTGRES_DB", POSTGRES_DB)
        .with_env("POSTGRES_USER", POSTGRES_USER)
        .with_env("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
        .with_exposed_ports(POSTGRES_PORT)
    )
    container.start()
    try:
        host = container.get_container_host_ip()
        port = container.get_exposed_port(POSTGRES_PORT)
        connection_url = _build_connection_url(host, port)
        _wait_until_connectable(connection_url)

        connection = pg8000.native.Connection(**_parse_database_url(connection_url))
        try:
            for statement in CREATE_SCHEMA_STATEMENTS:
                connection.run(statement)
        finally:
            connection.close()

        yield connection_url
    finally:
        container.stop()


@pytest.fixture(autouse=True)
def _clear_songs_between_tests(postgres_connection_url):
    yield
    connection = pg8000.native.Connection(**_parse_database_url(postgres_connection_url))
    try:
        connection.run("TRUNCATE TABLE song_artists, songs RESTART IDENTITY CASCADE")
    finally:
        connection.close()


@pytest.fixture(autouse=True)
def _point_settings_at_test_database(postgres_connection_url, monkeypatch):
    monkeypatch.setattr("app.config.settings.database_url", postgres_connection_url)
