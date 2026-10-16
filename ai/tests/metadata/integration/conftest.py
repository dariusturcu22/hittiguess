import pathlib
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

# Connecting to "localhost" can try an IPv6 address the container port is not bound
# to first, and on Windows each connection then stalls for seconds before falling back.
LOCALHOST_NAME = "localhost"
LOOPBACK_ADDRESS = "127.0.0.1"

CONNECTION_RETRY_ATTEMPTS = 60
CONNECTION_RETRY_DELAY_SECONDS = 1.0

# The AI service never runs Flyway, so the table comes from core's own migration file.
STAGE_PACE_MIGRATION = (
    pathlib.Path(__file__).resolve().parents[4]
    / "backend" / "src" / "main" / "resources" / "db" / "migration" / "V45__stage_pace.sql"
)
STATEMENT_SEPARATOR = ";"
RESET_PACE_ROWS_SQL = """
    UPDATE stage_pace SET next_slot_at = now(), cooldown_until = NULL, current_delay_ms = 0,
        avg_response_ms = NULL, updated_at = now()
"""


def _build_connection_url(host: str, port: str) -> str:
    return f"postgresql://{POSTGRES_USER}:{POSTGRES_PASSWORD}@{host}:{port}/{POSTGRES_DB}"


def open_connection(connection_url: str) -> pg8000.native.Connection:
    return pg8000.native.Connection(**_parse_database_url(connection_url))


def _wait_until_connectable(connection_url: str) -> None:
    last_connection_error: Exception | None = None
    for unused_attempt in range(CONNECTION_RETRY_ATTEMPTS):
        try:
            open_connection(connection_url).close()
            return
        except Exception as connection_error:
            last_connection_error = connection_error
            time.sleep(CONNECTION_RETRY_DELAY_SECONDS)
    raise RuntimeError(f"Postgres test container never became connectable: {last_connection_error}")


def _migration_statements() -> list[str]:
    migration_lines = [
        line for line in STAGE_PACE_MIGRATION.read_text(encoding="utf-8").splitlines() if not line.startswith("--")
    ]
    statements = "\n".join(migration_lines).split(STATEMENT_SEPARATOR)
    return [statement.strip() for statement in statements if statement.strip()]


@pytest.fixture(scope="session")
def pace_database_url():
    container = (
        DockerContainer(POSTGRES_IMAGE)
        .with_env("POSTGRES_DB", POSTGRES_DB)
        .with_env("POSTGRES_USER", POSTGRES_USER)
        .with_env("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
        .with_exposed_ports(POSTGRES_PORT)
    )
    container.start()
    try:
        container_host = container.get_container_host_ip()
        if container_host == LOCALHOST_NAME:
            container_host = LOOPBACK_ADDRESS
        connection_url = _build_connection_url(container_host, container.get_exposed_port(POSTGRES_PORT))
        _wait_until_connectable(connection_url)
        connection = open_connection(connection_url)
        try:
            for statement in _migration_statements():
                connection.run(statement)
        finally:
            connection.close()
        yield connection_url
    finally:
        container.stop()


@pytest.fixture(autouse=True)
def _reset_pace_rows(pace_database_url, monkeypatch):
    monkeypatch.setattr("app.config.settings.database_url", pace_database_url)
    connection = open_connection(pace_database_url)
    try:
        connection.run(RESET_PACE_ROWS_SQL)
    finally:
        connection.close()
