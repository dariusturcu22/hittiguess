from opentelemetry.util.re import parse_env_headers

from app.config import settings


def otlp_headers() -> dict[str, str] | None:
    """Parses otel_exporter_otlp_headers into the dict the SDK's exporters
    expect, using the same parser the SDK itself uses for the equivalent
    environment variable. Settings, not the process environment, is this
    service's source of truth for .env-provided configuration, so the value
    is read from there rather than relying on the SDK's own env var fallback."""
    if not settings.otel_exporter_otlp_headers:
        return None
    return parse_env_headers(settings.otel_exporter_otlp_headers)
