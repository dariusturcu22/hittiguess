import contextvars
import logging
import uuid
from collections.abc import Awaitable, Callable

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

REQUEST_ID_HEADER = "X-Request-Id"

logger = logging.getLogger(__name__)

_current_request_id: contextvars.ContextVar[str | None] = contextvars.ContextVar(
    "current_request_id", default=None
)


def get_current_request_id() -> str | None:
    return _current_request_id.get()


class CorrelationIdMiddleware(BaseHTTPMiddleware):
    """Reuses the correlation id set by the caller (the core service, on a
    core-service-to-AI-service call) or generates one, so one user action stays
    traceable across both services' logs. Echoes the id back on the response."""

    async def dispatch(
        self, request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        incoming_request_id = request.headers.get(REQUEST_ID_HEADER)
        request_id = incoming_request_id if incoming_request_id else str(uuid.uuid4())

        context_token = _current_request_id.set(request_id)
        try:
            logger.info("Handling request: %s %s", request.method, request.url.path)
            response = await call_next(request)
        finally:
            _current_request_id.reset(context_token)

        response.headers[REQUEST_ID_HEADER] = request_id
        return response
