from fastapi import HTTPException
import pytest

from app.auth import require_internal_api_key
from app.config import settings


def test_require_internal_api_key_accepts_the_configured_key():
    require_internal_api_key(x_internal_api_key=settings.internal_service_api_key)


def test_require_internal_api_key_rejects_a_wrong_key():
    with pytest.raises(HTTPException) as raised:
        require_internal_api_key(x_internal_api_key="not-the-real-key")

    assert raised.value.status_code == 401


def test_require_internal_api_key_rejects_an_empty_key():
    with pytest.raises(HTTPException) as raised:
        require_internal_api_key(x_internal_api_key="")

    assert raised.value.status_code == 401
