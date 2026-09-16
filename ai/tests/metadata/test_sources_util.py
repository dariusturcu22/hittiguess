from app.metadata.sources.util import (
    build_youtube_api_url,
    build_youtube_playlist_items_api_url,
    clean_youtube_text,
    escape_lucene,
    extract_youtube_playlist_id,
    extract_youtube_video_id,
    parse_iso8601_duration_seconds,
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


def test_parse_iso8601_duration_seconds_minutes_and_seconds():
    assert parse_iso8601_duration_seconds("PT3M52S") == 232


def test_parse_iso8601_duration_seconds_hours_minutes_seconds():
    assert parse_iso8601_duration_seconds("PT1H2M3S") == 3723


def test_parse_iso8601_duration_seconds_seconds_only():
    assert parse_iso8601_duration_seconds("PT45S") == 45


def test_parse_iso8601_duration_seconds_minutes_only():
    assert parse_iso8601_duration_seconds("PT10M") == 600


def test_parse_iso8601_duration_seconds_handles_missing_or_malformed():
    assert parse_iso8601_duration_seconds(None) is None
    assert parse_iso8601_duration_seconds("") is None
    assert parse_iso8601_duration_seconds("3:52") is None
    assert parse_iso8601_duration_seconds("P1D") is None


def test_extract_youtube_playlist_id_from_bare_id():
    assert extract_youtube_playlist_id("PLrAXtmRdnEQy6nuLMHjMZOz59Oq8B9bkA") == "PLrAXtmRdnEQy6nuLMHjMZOz59Oq8B9bkA"


def test_extract_youtube_playlist_id_from_playlist_url_with_list_param():
    assert (
        extract_youtube_playlist_id("https://youtube.com/playlist?list=PLrAXtmRdnEQy6nuLMHjMZOz59Oq8B9bkA")
        == "PLrAXtmRdnEQy6nuLMHjMZOz59Oq8B9bkA"
    )


def test_extract_youtube_playlist_id_from_watch_url_with_trailing_list_param():
    assert (
        extract_youtube_playlist_id("https://youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmRdnEQy6nuLMHjMZOz59Oq8B9bkA")
        == "PLrAXtmRdnEQy6nuLMHjMZOz59Oq8B9bkA"
    )


def test_extract_youtube_playlist_id_rejects_a_plain_video_url_with_no_list_param():
    assert extract_youtube_playlist_id("https://youtube.com/watch?v=dQw4w9WgXcQ") is None


def test_extract_youtube_playlist_id_rejects_invalid_input():
    assert extract_youtube_playlist_id(None) is None
    assert extract_youtube_playlist_id("") is None


def test_build_youtube_playlist_items_api_url_without_page_token():
    url = build_youtube_playlist_items_api_url("PL123", "my-key")
    assert url == (
        "https://www.googleapis.com/youtube/v3/playlistItems"
        "?part=contentDetails&playlistId=PL123&maxResults=50&key=my-key"
    )


def test_build_youtube_playlist_items_api_url_with_page_token():
    url = build_youtube_playlist_items_api_url("PL123", "my-key", "next-page-token")
    assert url.endswith("&pageToken=next-page-token")
