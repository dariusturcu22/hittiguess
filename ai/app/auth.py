from fastapi import Header, HTTPException, status

from app.config import settings

INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key"


def require_internal_api_key(
    x_internal_api_key: str = Header(..., alias=INTERNAL_API_KEY_HEADER),
) -> None:
    if x_internal_api_key != settings.internal_service_api_key:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid internal API key")
