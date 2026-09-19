from app.clients import deepinfra_client


def test_client_uses_the_configured_request_timeout():
    assert deepinfra_client.client.timeout == deepinfra_client.REQUEST_TIMEOUT_SECONDS


def test_client_targets_the_deepinfra_openai_compatible_base_url():
    assert str(deepinfra_client.client.base_url).rstrip("/") == deepinfra_client.DEEPINFRA_BASE_URL
