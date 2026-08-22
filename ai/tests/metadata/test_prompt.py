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
        "wikipedia": None,
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


def test_build_omits_empty_optional_sources():
    text = prompt.build(_base_metadata())
    assert "=== MUSICBRAINZ DATABASE" not in text
    assert "=== WIKIPEDIA ===" not in text
    assert "=== GENIUS ===" not in text


def test_build_includes_musicbrainz_results_when_present():
    text = prompt.build(
        _base_metadata(
            musicbrainz=[
                {"title": "Test Song", "artist": "Test Artist", "release_date": "1999-05-01", "score": "95"}
            ]
        )
    )
    assert "=== MUSICBRAINZ DATABASE (Most Authoritative) ===" in text
    assert '"Test Song" by Test Artist - Year: 1999 (Match Score: 95/100)' in text


def test_build_includes_wikipedia_and_genius_when_present():
    text = prompt.build(
        _base_metadata(
            wikipedia={"title": "Test Song (song)", "release_info": "Released: 1999"},
            genius={"title": "Test Song", "artist": "Test Artist", "release_date": "1999-05-01"},
        )
    )
    assert "Page: Test Song (song)" in text
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
