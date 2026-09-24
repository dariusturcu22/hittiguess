from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from app.metadata.router import router as metadata_router
from app.observability.logging_config import configure_logging
from app.observability.request_context import CorrelationIdMiddleware
from app.observability.sentry import init_sentry
from app.observability.tracing import setup_tracing
from app.config import settings

configure_logging()
init_sentry()

if not settings.internal_service_api_key.strip():
    raise RuntimeError("INTERNAL_SERVICE_API_KEY must be configured")

app = FastAPI(title="hittiguess AI microservice", openapi_url=None, docs_url=None, redoc_url=None)
app.add_middleware(CorrelationIdMiddleware)
app.include_router(metadata_router)

setup_tracing(app)
Instrumentator().instrument(app)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
