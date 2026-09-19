import httpx
import respx

from app.metadata.sources import wikidata


def test_pick_best_match_prefers_artist_mentioned_in_description():
    matches = [
        {"id": "Q1", "description": "1985 song by a different artist"},
        {"id": "Q2", "description": "2013 song by Test Artist"},
    ]
    best = wikidata.pick_best_match(matches, "Test Artist")
    assert best["id"] == "Q2"


def test_pick_best_match_falls_back_to_music_keyword_description():
    matches = [
        {"id": "Q1", "description": "a restaurant"},
        {"id": "Q2", "description": "1999 single"},
    ]
    best = wikidata.pick_best_match(matches, "Nobody Mentioned")
    assert best["id"] == "Q2"


def test_pick_best_match_returns_none_when_nothing_matches():
    matches = [{"id": "Q1", "description": "a restaurant"}, {"id": "Q2", "description": "a film"}]
    assert wikidata.pick_best_match(matches, "Nobody Mentioned") is None


def test_pick_best_match_returns_none_for_empty_matches():
    assert wikidata.pick_best_match([], "Test Artist") is None


def test_extract_publication_date_prefers_preferred_rank():
    entity = {
        "claims": {
            "P577": [
                {"rank": "normal", "mainsnak": {"snaktype": "value", "datavalue": {"value": {"time": "+2015-00-00T00:00:00Z"}}}},
                {"rank": "preferred", "mainsnak": {"snaktype": "value", "datavalue": {"value": {"time": "+1999-00-00T00:00:00Z"}}}},
            ]
        }
    }
    assert wikidata.extract_publication_date(entity) == "+1999-00-00T00:00:00Z"


def test_extract_publication_date_returns_none_when_absent():
    assert wikidata.extract_publication_date({"claims": {}}) is None


def test_get_part_of_returns_entity_id():
    entity = {"claims": {"P361": [{"mainsnak": {"datavalue": {"value": {"id": "Q42"}}}}]}}
    assert wikidata.get_part_of(entity) == "Q42"


def test_get_part_of_returns_none_when_absent():
    assert wikidata.get_part_of({"claims": {}}) is None


def _wikidata_response(request: httpx.Request) -> httpx.Response:
    action = request.url.params.get("action")
    if action == "wbsearchentities":
        return httpx.Response(200, json={"search": [{"id": "Q1", "description": "1999 song by Test Artist"}]})
    if action == "wbgetentities":
        entity_id = request.url.params["ids"]
        if entity_id == "Q1":
            return httpx.Response(
                200,
                json={
                    "entities": {
                        "Q1": {
                            "claims": {
                                "P577": [
                                    {"rank": "normal", "mainsnak": {"snaktype": "value", "datavalue": {"value": {"time": "+1999-00-00T00:00:00Z"}}}}
                                ],
                                "P361": [{"mainsnak": {"datavalue": {"value": {"id": "Q2"}}}}],
                            }
                        }
                    }
                },
            )
        return httpx.Response(
            200,
            json={
                "entities": {
                    "Q2": {
                        "claims": {
                            "P577": [
                                {"rank": "normal", "mainsnak": {"snaktype": "value", "datavalue": {"value": {"time": "+1998-00-00T00:00:00Z"}}}}
                            ]
                        }
                    }
                }
            },
        )
    return httpx.Response(400)


@respx.mock
def test_search_returns_track_and_album_candidates_via_part_of(mocker):
    mocker.patch("app.metadata.sources.wikidata.time.sleep")
    respx.get("https://www.wikidata.org/w/api.php").mock(side_effect=_wikidata_response)

    candidates = wikidata.search("Test Song", "Test Artist")

    assert len(candidates) == 2
    track_candidate, album_candidate = candidates
    assert track_candidate == {"query": "track", "entity_id": "Q1", "description": "1999 song by Test Artist", "date": "+1999-00-00T00:00:00Z"}
    assert album_candidate["query"] == "album"
    assert album_candidate["entity_id"] == "Q2"
    assert album_candidate["date"] == "+1998-00-00T00:00:00Z"


@respx.mock
def test_search_returns_empty_list_when_no_match(mocker):
    mocker.patch("app.metadata.sources.wikidata.time.sleep")
    respx.get("https://www.wikidata.org/w/api.php").mock(return_value=httpx.Response(200, json={"search": []}))

    assert wikidata.search("Test Song", "Test Artist") == []


@respx.mock
def test_search_returns_empty_list_on_request_failure(mocker):
    mocker.patch("app.metadata.sources.wikidata.time.sleep")
    respx.get("https://www.wikidata.org/w/api.php").mock(return_value=httpx.Response(500))

    assert wikidata.search("Test Song", "Test Artist") == []
