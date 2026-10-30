import json
from pathlib import Path

import httpx
import respx

from app.metadata.sources import wikidata

WIKIDATA_FIXTURES = Path(__file__).parent / "fixtures" / "wikidata"


def test_pick_best_match_prefers_artist_mentioned_in_description():
    matches = [
        {"id": "Q1", "description": "1985 song by a different artist"},
        {"id": "Q2", "description": "2013 song by Test Artist"},
    ]
    best = wikidata.pick_best_match(matches, ["Test Artist"])
    assert best["id"] == "Q2"


def test_pick_best_match_falls_back_to_music_keyword_description():
    matches = [
        {"id": "Q1", "description": "a restaurant"},
        {"id": "Q2", "description": "1999 single"},
    ]
    best = wikidata.pick_best_match(matches, ["Nobody Mentioned"])
    assert best["id"] == "Q2"


def test_pick_best_match_returns_none_when_nothing_matches():
    matches = [{"id": "Q1", "description": "a restaurant"}, {"id": "Q2", "description": "a film"}]
    assert wikidata.pick_best_match(matches, ["Nobody Mentioned"]) is None


def test_pick_best_match_returns_none_for_empty_matches():
    assert wikidata.pick_best_match([], ["Test Artist"]) is None


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
    if action == "query":
        return httpx.Response(200, json={"query": {"search": []}})
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
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get("https://www.wikidata.org/w/api.php").mock(side_effect=_wikidata_response)

    candidates = wikidata.search("Test Song", ["Test Artist"])

    assert len(candidates) == 2
    track_candidate, album_candidate = candidates
    assert track_candidate == {"query": "track", "entity_id": "Q1", "description": "1999 song by Test Artist", "date": "+1999-00-00T00:00:00Z"}
    assert album_candidate["query"] == "album"
    assert album_candidate["entity_id"] == "Q2"
    assert album_candidate["date"] == "+1998-00-00T00:00:00Z"


@respx.mock
def test_search_returns_empty_list_when_no_match(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get("https://www.wikidata.org/w/api.php").mock(return_value=httpx.Response(200, json={"search": []}))

    assert wikidata.search("Test Song", ["Test Artist"]) == []


@respx.mock
def test_search_returns_empty_list_on_request_failure(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get("https://www.wikidata.org/w/api.php").mock(return_value=httpx.Response(500))

    assert wikidata.search("Test Song", ["Test Artist"]) == []


@respx.mock
def test_get_sitelinks_count_counts_linked_language_editions(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get("https://www.wikidata.org/w/api.php").mock(
        return_value=httpx.Response(200, json={"entities": {"Q1": {"sitelinks": {"enwiki": {}, "rowiki": {}, "dewiki": {}}}}})
    )

    assert wikidata.get_sitelinks_count("Q1") == 3


@respx.mock
def test_get_sitelinks_count_returns_none_when_the_entity_is_missing(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get("https://www.wikidata.org/w/api.php").mock(return_value=httpx.Response(200, json={"entities": {}}))

    assert wikidata.get_sitelinks_count("Q1") is None


@respx.mock
def test_get_sitelinks_count_returns_none_on_request_failure(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    respx.get("https://www.wikidata.org/w/api.php").mock(return_value=httpx.Response(500))

    assert wikidata.get_sitelinks_count("Q1") is None


@respx.mock
def test_wikidata_matches_whats_my_name_rihanna_drake(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    search_json = json.loads((WIKIDATA_FIXTURES / "whats-my-name-search.json").read_text(encoding="utf-8"))
    entity_json = json.loads((WIKIDATA_FIXTURES / "whats-my-name-entity.json").read_text(encoding="utf-8"))
    performers_json = json.loads((WIKIDATA_FIXTURES / "whats-my-name-performers.json").read_text(encoding="utf-8"))

    def _mock_response(request: httpx.Request) -> httpx.Response:
        action = request.url.params.get("action")
        if action == "query":
            return httpx.Response(200, json={"query": {"search": []}})
        if action == "wbsearchentities":
            return httpx.Response(200, json=search_json)
        if action == "wbgetentities":
            ids = request.url.params.get("ids", "")
            if ids == "Q16051784":
                return httpx.Response(200, json=entity_json)
            if "Q36844" in ids or "Q33240" in ids:
                return httpx.Response(200, json=performers_json)
            if ids == "Q208340":
                return httpx.Response(
                    200,
                    json={
                        "entities": {
                            "Q208340": {
                                "claims": {
                                    "P577": [
                                        {"rank": "normal", "mainsnak": {"snaktype": "value", "datavalue": {"value": {"time": "+2010-11-12T00:00:00Z"}}}}
                                    ]
                                }
                            }
                        }
                    },
                )
        return httpx.Response(404)

    respx.get("https://www.wikidata.org/w/api.php").mock(side_effect=_mock_response)

    candidates = wikidata.search("What's My Name?", ["Rihanna", "Drake"])
    assert len(candidates) >= 1
    assert candidates[0]["entity_id"] == "Q16051784"
    assert str(candidates[0]["date"]).startswith("+2010")


@respx.mock
def test_wikidata_matches_yeah_usher_lil_jon_ludacris(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    search_json = json.loads((WIKIDATA_FIXTURES / "yeah-search.json").read_text(encoding="utf-8"))
    entity_json = json.loads((WIKIDATA_FIXTURES / "yeah-entity.json").read_text(encoding="utf-8"))
    performers_json = json.loads((WIKIDATA_FIXTURES / "yeah-performers.json").read_text(encoding="utf-8"))

    def _mock_response(request: httpx.Request) -> httpx.Response:
        action = request.url.params.get("action")
        if action == "query":
            return httpx.Response(200, json={"query": {"search": []}})
        if action == "wbsearchentities":
            return httpx.Response(200, json=search_json)
        if action == "wbgetentities":
            ids = request.url.params.get("ids", "")
            if ids == "Q744297":
                return httpx.Response(200, json=entity_json)
            if any(performer_id in ids for performer_id in ("Q165911", "Q294449", "Q193676")):
                return httpx.Response(200, json=performers_json)
            if ids == "Q1125266":
                return httpx.Response(
                    200,
                    json={
                        "entities": {
                            "Q1125266": {
                                "claims": {
                                    "P577": [
                                        {"rank": "normal", "mainsnak": {"snaktype": "value", "datavalue": {"value": {"time": "+2004-03-23T00:00:00Z"}}}}
                                    ]
                                }
                            }
                        }
                    },
                )
        return httpx.Response(404)

    respx.get("https://www.wikidata.org/w/api.php").mock(side_effect=_mock_response)

    candidates = wikidata.search("Yeah!", ["Usher", "Lil Jon", "Ludacris"])
    assert len(candidates) >= 1
    assert candidates[0]["entity_id"] == "Q744297"
    assert str(candidates[0]["date"]).startswith("+2004")


@respx.mock
def test_wikidata_matches_bang_bang_jessie_j_ariana_nicki(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")
    search_json = json.loads((WIKIDATA_FIXTURES / "bang-bang-search.json").read_text(encoding="utf-8"))
    entity_json = json.loads((WIKIDATA_FIXTURES / "bang-bang-entity.json").read_text(encoding="utf-8"))
    performers_json = json.loads((WIKIDATA_FIXTURES / "bang-bang-performers.json").read_text(encoding="utf-8"))

    def _mock_response(request: httpx.Request) -> httpx.Response:
        action = request.url.params.get("action")
        if action == "query":
            return httpx.Response(200, json={"query": {"search": []}})
        if action == "wbsearchentities":
            return httpx.Response(200, json=search_json)
        if action == "wbgetentities":
            ids = request.url.params.get("ids", "")
            if ids == "Q17468562":
                return httpx.Response(200, json=entity_json)
            if "Q18814" in ids or "Q151892" in ids:
                return httpx.Response(200, json=performers_json)
            if ids == "Q17592845":
                return httpx.Response(
                    200,
                    json={
                        "entities": {
                            "Q17592845": {
                                "claims": {
                                    "P577": [
                                        {"rank": "normal", "mainsnak": {"snaktype": "value", "datavalue": {"value": {"time": "+2014-10-10T00:00:00Z"}}}}
                                    ]
                                }
                            }
                        }
                    },
                )
        return httpx.Response(404)

    respx.get("https://www.wikidata.org/w/api.php").mock(side_effect=_mock_response)

    candidates = wikidata.search("Bang Bang", ["Jessie J", "Ariana Grande", "Nicki Minaj"])
    assert len(candidates) >= 1
    assert candidates[0]["entity_id"] == "Q17468562"
    assert str(candidates[0]["date"]).startswith("+2014")


def test_performer_check_does_not_match_a_name_inside_a_longer_name():
    assert not wikidata._performer_matches_main_artist(["Asia"], ["Sia"])
    assert wikidata._performer_matches_main_artist(["Sia"], ["Sia"])
    assert wikidata._performer_matches_main_artist(["Daddy Yankee"], ["Daddy Yankee & Snow"])


@respx.mock
def test_full_text_search_finds_a_song_with_a_common_title(mocker):
    mocker.patch("app.metadata.sources.pacing.time.sleep")

    def respond(request: httpx.Request) -> httpx.Response:
        action = request.url.params.get("action")
        if action == "query":
            assert request.url.params["srsearch"] == "Burn Ellie Goulding"
            return httpx.Response(200, json={"query": {"search": [{"title": "Q14200893"}, {"title": "Q61446164"}]}})
        ids = request.url.params.get("ids")
        if request.url.params.get("props") == "labels|descriptions":
            return httpx.Response(200, json={"entities": {
                "Q14200893": {"labels": {"mul": {"value": "Burn"}}, "descriptions": {"en": {"value": "2013 single by Ellie Goulding"}}},
                "Q61446164": {"labels": {"en": {"value": "Effects of fluoxetine"}}, "descriptions": {"en": {"value": "scientific article"}}},
            }})
        if ids == "Q14200893":
            return httpx.Response(200, json={"entities": {"Q14200893": {"claims": {"P577": [
                {"rank": "normal", "mainsnak": {"snaktype": "value", "datavalue": {"value": {"time": "+2013-07-05T00:00:00Z"}}}}]}}}})
        return httpx.Response(404)

    respx.get("https://www.wikidata.org/w/api.php").mock(side_effect=respond)

    candidates = wikidata.search("Burn", ["Ellie Goulding"])

    assert candidates == [{"query": "track", "entity_id": "Q14200893", "description": "2013 single by Ellie Goulding",
                           "date": "+2013-07-05T00:00:00Z"}]


def test_performer_labels_fall_back_to_the_multilingual_label():
    assert wikidata._english_text({"mul": {"value": "Rihanna"}}) == "Rihanna"
    assert wikidata._english_text({"en": {"value": "Shakira"}, "mul": {"value": "Shakira Mebarak"}}) == "Shakira"
