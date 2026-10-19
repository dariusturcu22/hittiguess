from app.observability.otlp import otlp_headers


def test_otlp_headers_returns_none_when_unset(mocker):
    mocker.patch("app.observability.otlp.settings").otel_exporter_otlp_headers = None

    assert otlp_headers() is None


def test_otlp_headers_parses_the_standard_otlp_header_format(mocker):
    mocker.patch(
        "app.observability.otlp.settings"
    ).otel_exporter_otlp_headers = "Authorization=Basic%20abc123"

    assert otlp_headers() == {"authorization": "Basic abc123"}
