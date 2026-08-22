# Paused pending a review of API usage, cost, and User-Agent contact info
# across all metadata sources (see genius.py). MusicBrainz's endpoint itself
# is official and documented (build_musicbrainz_api_url in sources/util.py),
# unlike Genius's; this is a deliberate pause, not a compliance fix.


def search(title: str, artist: str) -> list[dict[str, str]]:
    return []
