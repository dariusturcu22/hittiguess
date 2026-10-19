import httpx

from app.metadata.sources.util import METADATA_SOURCE_USER_AGENT

LOGIN_REQUEST_TIMEOUT_SECONDS = 10.0


def build_authenticated_client(api_url: str, bot_username: str | None, bot_password: str | None) -> httpx.Client | None:
    """A Special:BotPasswords login unlocks a MediaWiki wiki's authenticated
    rate-limit tier (200/minute, versus 10/minute anonymous). Falls back to
    None, meaning anonymous access, when no credentials are configured or the
    login itself fails, a wrong or expired bot password shouldn't take the
    metadata pipeline down, only make Wikidata/Wikipedia lookups slower. A bot
    password is issued per wiki, one wiki's credentials don't authenticate
    against another. MediaWiki's login flow is a two-step token-then-login
    exchange, not HTTP Basic Auth, and requires persisting the session cookie
    across every subsequent call, hence a real httpx.Client rather than
    one-off requests."""
    if not bot_username or not bot_password:
        return None

    try:
        client = httpx.Client(headers={"User-Agent": METADATA_SOURCE_USER_AGENT})
        token_response = client.get(
            api_url,
            params={"action": "query", "meta": "tokens", "type": "login", "format": "json"},
            timeout=LOGIN_REQUEST_TIMEOUT_SECONDS,
        )
        login_token = token_response.json()["query"]["tokens"]["logintoken"]
        login_response = client.post(
            api_url,
            data={
                "action": "login",
                "lgname": bot_username,
                "lgpassword": bot_password,
                "lgtoken": login_token,
                "format": "json",
            },
            timeout=LOGIN_REQUEST_TIMEOUT_SECONDS,
        )
        login_result = login_response.json().get("login", {}).get("result")
        return client if login_result == "Success" else None
    except Exception:
        return None
