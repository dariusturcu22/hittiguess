# Paused pending a review of API usage, cost, and User-Agent contact info
# across all metadata sources (see genius.py). Wikipedia's endpoint itself
# is official and documented (build_wikipedia_search_url/build_wikipedia_page_url
# in sources/util.py), unlike Genius's; this is a deliberate pause, not a
# compliance fix.


def search(title: str, artist: str) -> dict[str, str] | None:
    return None
