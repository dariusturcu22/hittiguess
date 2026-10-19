import pytest

from app.dedup.database import _parse_database_url

PLAIN_DATABASE_URL = "postgresql://core_user:core_password@localhost:5500/core"
MANAGED_DATABASE_URL = "postgresql://core_user:core_password@db.example.test/core?sslmode={ssl_mode}"
MANAGED_HOST = "db.example.test"
DEFAULT_PORT = 5432


def test_plain_url_has_no_tls_and_keeps_its_parts():
    parameters = _parse_database_url(PLAIN_DATABASE_URL)

    assert parameters == {
        "user": "core_user",
        "password": "core_password",
        "host": "localhost",
        "port": 5500,
        "database": "core",
    }


@pytest.mark.parametrize("ssl_mode", ["require", "verify-ca", "verify-full"])
def test_tls_requiring_ssl_modes_enable_tls(ssl_mode):
    parameters = _parse_database_url(MANAGED_DATABASE_URL.format(ssl_mode=ssl_mode))

    assert parameters["ssl_context"] is True
    assert parameters["host"] == MANAGED_HOST
    assert parameters["port"] == DEFAULT_PORT
    assert parameters["database"] == "core"


@pytest.mark.parametrize("ssl_mode", ["disable", "allow", "prefer"])
def test_optional_ssl_modes_leave_tls_off(ssl_mode):
    parameters = _parse_database_url(MANAGED_DATABASE_URL.format(ssl_mode=ssl_mode))

    assert "ssl_context" not in parameters
