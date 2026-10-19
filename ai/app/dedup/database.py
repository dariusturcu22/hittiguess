from urllib.parse import parse_qs, urlparse

import pg8000.native
from pgvector.pg8000 import register_vector

from app.config import settings

DEFAULT_POSTGRES_PORT = 5432
SSL_MODE_QUERY_PARAMETER = "sslmode"
TLS_REQUIRING_SSL_MODES = frozenset({"require", "verify-ca", "verify-full"})


def _parse_database_url(database_url: str) -> dict[str, object]:
    parsed_url = urlparse(database_url)
    connection_parameters: dict[str, object] = {
        "user": parsed_url.username,
        "password": parsed_url.password,
        "host": parsed_url.hostname,
        "port": parsed_url.port or DEFAULT_POSTGRES_PORT,
        "database": parsed_url.path.lstrip("/"),
    }
    ssl_modes = parse_qs(parsed_url.query).get(SSL_MODE_QUERY_PARAMETER, [])
    if any(ssl_mode in TLS_REQUIRING_SSL_MODES for ssl_mode in ssl_modes):
        connection_parameters["ssl_context"] = True
    return connection_parameters


def get_connection() -> pg8000.native.Connection:
    """Opens a direct connection to the core transactional Postgres+pgvector
    instance. The core service owns the schema through Flyway; this
    connection only reads and writes rows, never DDL. pg8000 is a pure-Python
    driver, chosen over psycopg's compiled bindings so this dependency needs
    no native extension to load; see docs/DECISIONS.md."""
    connection = pg8000.native.Connection(**_parse_database_url(settings.database_url))
    register_vector(connection)
    return connection
