from app.metadata.sources.util import (
    build_musicbrainz_api_url,
    build_wikipedia_page_url,
    build_wikipedia_search_url,
    build_youtube_api_url,
    clean_youtube_text,
    escape_lucene,
    extract_release_date_from_text,
    extract_youtube_video_id,
)


def test_extract_youtube_video_id_from_watch_url():
    assert extract_youtube_video_id("https://www.youtube.com/watch?v=dQw4w9WgXcQ") == "dQw4w9WgXcQ"


def test_extract_youtube_video_id_from_watch_url_with_extra_params():
    assert (
        extract_youtube_video_id("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=xyz")
        == "dQw4w9WgXcQ"
    )


def test_extract_youtube_video_id_from_short_url():
    assert extract_youtube_video_id("https://youtu.be/dQw4w9WgXcQ") == "dQw4w9WgXcQ"


def test_extract_youtube_video_id_from_embed_url():
    assert extract_youtube_video_id("https://www.youtube.com/embed/dQw4w9WgXcQ") == "dQw4w9WgXcQ"


def test_extract_youtube_video_id_from_bare_id():
    assert extract_youtube_video_id("dQw4w9WgXcQ") == "dQw4w9WgXcQ"


def test_extract_youtube_video_id_rejects_invalid_input():
    assert extract_youtube_video_id("not a youtube url") is None
    assert extract_youtube_video_id(None) is None
    assert extract_youtube_video_id("") is None


def test_clean_youtube_text_strips_official_video_markers():
    assert (
        clean_youtube_text("Rick Astley - Never Gonna Give You Up (Official Music Video)")
        == "Rick Astley - Never Gonna Give You Up"
    )


def test_clean_youtube_text_strips_featuring_suffix():
    assert clean_youtube_text("Song Title feat. Some Artist") == "Song Title"


def test_clean_youtube_text_handles_none():
    assert clean_youtube_text(None) == ""


def test_extract_release_date_from_text_released_on():
    assert (
        extract_release_date_from_text("The single was released on January 15, 1999 worldwide.")
        == "Released: January 15, 1999"
    )


def test_extract_release_date_from_text_released_in_month_year():
    assert (
        extract_release_date_from_text("It was released in March 1985 to critical acclaim.")
        == "Released: March 1985"
    )


def test_extract_release_date_from_text_bare_year():
    assert extract_release_date_from_text("This is a 1988 single from their debut album.") == "Released: 1988"


def test_extract_release_date_from_text_no_match():
    assert extract_release_date_from_text("No date information available here.") == "No release date found"


def test_extract_release_date_from_text_empty():
    assert extract_release_date_from_text("") == "No release date found"
    assert extract_release_date_from_text(None) == "No release date found"


def test_escape_lucene_escapes_special_characters():
    assert escape_lucene("A+B") == "A\\+B"
    assert escape_lucene('quote"here') == 'quote\\"here'


def test_escape_lucene_handles_none():
    assert escape_lucene(None) == ""


def test_build_youtube_api_url_includes_id_and_key():
    url = build_youtube_api_url("dQw4w9WgXcQ", "my-key")
    assert url == (
        "https://www.googleapis.com/youtube/v3/videos"
        "?part=snippet,contentDetails&id=dQw4w9WgXcQ&key=my-key"
    )


def test_build_musicbrainz_api_url_encodes_query():
    url = build_musicbrainz_api_url('recording:"Song Title"')
    assert url.startswith("https://musicbrainz.org/ws/2/recording/?query=")
    assert "%22Song" in url
    assert url.endswith("&fmt=json&limit=10")


def test_build_wikipedia_search_url_encodes_search_term():
    url = build_wikipedia_search_url("Blinding Lights song")
    assert url.startswith("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=")
    assert "Blinding%20Lights%20song" in url


def test_build_wikipedia_page_url_encodes_title():
    url = build_wikipedia_page_url("Blinding Lights")
    assert "titles=Blinding%20Lights" in url
    assert "prop=extracts" in url
