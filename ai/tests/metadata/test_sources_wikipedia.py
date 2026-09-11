import httpx
import respx

from app.metadata.sources import wikipedia


def test_select_best_page_prefers_song_disambiguator_for_track_query():
    results = [
        {"title": "Blinding Lights (album)"},
        {"title": "Blinding Lights (song)"},
    ]
    best = wikipedia.select_best_page(results, "Blinding Lights", "Test Artist", query_type="track")
    assert best["title"] == "Blinding Lights (song)"


def test_select_best_page_prefers_album_disambiguator_for_album_query():
    results = [
        {"title": "Blinding Lights (song)"},
        {"title": "Blinding Lights (album)"},
    ]
    best = wikipedia.select_best_page(results, "Blinding Lights", "Test Artist", query_type="album")
    assert best["title"] == "Blinding Lights (album)"


def test_select_best_page_falls_back_to_top_result_when_no_title_match():
    results = [{"title": "Something Unrelated"}]
    best = wikipedia.select_best_page(results, "Test Song", "Test Artist")
    assert best["title"] == "Something Unrelated"


def test_select_best_page_avoids_bare_artist_page_when_an_alternative_exists():
    # An eponymous song's title-matching filter can also catch the band's own bare
    # artist page ("Genesis" the artist matches a "Genesis" song title search).
    results = [
        {"title": "Genesis"},
        {"title": "Genesis (disambiguation)"},
    ]
    best = wikipedia.select_best_page(results, "Genesis", "Genesis")
    assert best["title"] == "Genesis (disambiguation)"


def test_select_best_page_returns_none_for_empty_results():
    assert wikipedia.select_best_page([], "Test Song", "Test Artist") is None


def _wikipedia_response(request: httpx.Request) -> httpx.Response:
    params = request.url.params
    if params.get("list") == "search":
        search_term = params["srsearch"]
        if "Test Album" in search_term:
            return httpx.Response(200, json={"query": {"search": [{"title": "Test Album (album)"}]}})
        return httpx.Response(200, json={"query": {"search": [{"title": "Test Song (song)"}]}})
    if params.get("prop") == "extracts":
        page_title = params["titles"]
        return httpx.Response(200, json={"query": {"pages": {"1": {"extract": f"{page_title} was released in 1999."}}}})
    return httpx.Response(400)


@respx.mock
def test_search_returns_track_and_album_entries(mocker):
    mocker.patch("app.metadata.sources.wikipedia.time.sleep")
    respx.get("https://en.wikipedia.org/w/api.php").mock(side_effect=_wikipedia_response)

    entries = wikipedia.search("Test Song", "Test Artist", album="Test Album")

    assert len(entries) == 2
    assert entries[0] == {"query": "track", "page_title": "Test Song (song)", "extract": "Test Song (song) was released in 1999."}
    assert entries[1]["query"] == "album"
    assert entries[1]["page_title"] == "Test Album (album)"


@respx.mock
def test_search_skips_album_query_when_no_album_given(mocker):
    mocker.patch("app.metadata.sources.wikipedia.time.sleep")
    respx.get("https://en.wikipedia.org/w/api.php").mock(side_effect=_wikipedia_response)

    entries = wikipedia.search("Test Song", "Test Artist")

    assert all(entry["query"] == "track" for entry in entries)


@respx.mock
def test_search_returns_empty_list_when_no_match(mocker):
    mocker.patch("app.metadata.sources.wikipedia.time.sleep")
    respx.get("https://en.wikipedia.org/w/api.php").mock(return_value=httpx.Response(200, json={"query": {"search": []}}))

    assert wikipedia.search("Test Song", "Test Artist") == []


@respx.mock
def test_search_returns_empty_list_on_request_failure(mocker):
    mocker.patch("app.metadata.sources.wikipedia.time.sleep")
    respx.get("https://en.wikipedia.org/w/api.php").mock(return_value=httpx.Response(500))

    assert wikipedia.search("Test Song", "Test Artist") == []
