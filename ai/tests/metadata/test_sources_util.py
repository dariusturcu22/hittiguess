from app.metadata.sources.util import (
    build_youtube_api_url,
    clean_youtube_text,
    escape_lucene,
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
