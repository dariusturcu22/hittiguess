# Disabled: the reference implementation this was ported from called
# genius.com/api/search/multi, the undocumented endpoint the Genius website's
# own search bar uses, with a browser-spoofed User-Agent. That's not Genius's
# official API (api.genius.com, which requires a registered client and a
# bearer token) and violates CLAUDE.md's official-APIs-only rule. Pending a
# real integration against api.genius.com.


def search(title: str, artist: str) -> dict[str, str] | None:
    return None
