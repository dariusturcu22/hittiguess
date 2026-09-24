from fastapi import Depends, FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from app.metadata.fast_tier_router import router as fast_tier_router
from app.metadata.router import router as metadata_router
from app.observability.logging_config import configure_logging
from app.observability.request_context import CorrelationIdMiddleware
from app.observability.sentry import init_sentry
from app.observability.tracing import setup_tracing
from app.auth import require_internal_api_key
from app.config import settings

configure_logging()
init_sentry()

def require_internal_api_key_configured(internal_api_key: str) -> None:
    if not internal_api_key.strip():
        raise RuntimeError("INTERNAL_SERVICE_API_KEY must be configured")


require_internal_api_key_configured(settings.internal_service_api_key)

app = FastAPI(title="hittiguess AI microservice", openapi_url=None, docs_url=None, redoc_url=None)
app.add_middleware(CorrelationIdMiddleware)
app.include_router(metadata_router)
app.include_router(fast_tier_router)

setup_tracing(app)
# Only the Alloy scraper reads metrics, sending the same internal key the backend uses.
Instrumentator().instrument(app).expose(app, dependencies=[Depends(require_internal_api_key)])


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
