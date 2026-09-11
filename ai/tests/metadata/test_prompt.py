from app.metadata import prompt


def _base_metadata(**overrides):
    data = {
        "youtube": {
            "video_title": "Test Song",
            "channel_title": "Test Artist",
            "upload_year": "2020",
            "description": "Released in 1999.",
        },
        "musicbrainz": [],
        "wikidata": [],
        "wikipedia": [],
        "genius": None,
    }
    data.update(overrides)
    return data


def test_build_includes_youtube_section():
    text = prompt.build(_base_metadata())
    assert "=== YOUTUBE VIDEO DATA ===" in text
    assert "Video Title: Test Song" in text
    assert "Channel Name: Test Artist" in text
    assert "Released in 1999." in text


def test_build_shows_no_candidates_for_empty_structured_sources():
    text = prompt.build(_base_metadata())
    assert "=== MUSICBRAINZ DATABASE (Most Authoritative) ===" in text
    assert "=== WIKIDATA ===" in text
    assert "=== WIKIPEDIA ===" in text
    assert "(no candidates returned)" in text
    assert "(no article extract available)" in text
    assert "=== GENIUS ===" not in text


def test_build_includes_musicbrainz_results_when_present():
    text = prompt.build(
        _base_metadata(
            musicbrainz=[
                {"query": "track", "title": "Test Song", "artist": "Test Artist", "date": "1999-05-01", "type": "Single", "score": 95},
                {"query": "album", "title": "Test Album", "artist": "Test Artist", "date": "2001-01-01", "type": "Album", "score": 90},
            ]
        )
    )
    assert "Track query:" in text
    assert "Album query:" in text
    assert '"Test Song" by Test Artist - date: 1999-05-01 - type: Single - score: 95/100' in text
    assert '"Test Album" by Test Artist - date: 2001-01-01 - type: Album - score: 90/100' in text


def test_build_includes_wikidata_results_when_present():
    text = prompt.build(
        _base_metadata(
            wikidata=[{"query": "track", "entity_id": "Q1", "description": "1999 song", "date": "+1999-00-00T00:00:00Z"}]
        )
    )
    assert "[track] 1999 song - publication date: +1999-00-00T00:00:00Z" in text


def test_build_includes_wikipedia_results_when_present():
    text = prompt.build(
        _base_metadata(
            wikipedia=[{"query": "track", "page_title": "Test Song (song)", "extract": "Released in 1999."}]
        )
    )
    assert "[track] 'Test Song (song)':" in text
    assert "Released in 1999." in text


def test_build_includes_genius_when_present():
    text = prompt.build(
        _base_metadata(genius={"title": "Test Song", "artist": "Test Artist", "release_date": "1999-05-01"})
    )
    assert '"Test Song" by Test Artist - Year: 1999' in text


def test_build_truncates_long_description():
    long_description = "x" * 2000
    text = prompt.build(_base_metadata(youtube={
        "video_title": "Test Song",
        "channel_title": "Test Artist",
        "upload_year": "2020",
        "description": long_description,
    }))
    assert ("x" * 1500 + "...") in text
    assert ("x" * 1501) not in text


def test_build_notes_missing_description():
    text = prompt.build(_base_metadata(youtube={
        "video_title": "Test Song",
        "channel_title": "Test Artist",
        "upload_year": "2020",
        "description": "",
    }))
    assert "Video Description: (none)" in text
