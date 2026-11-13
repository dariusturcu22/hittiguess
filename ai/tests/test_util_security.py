import pytest
from app.metadata.sources.util import extract_youtube_playlist_id, extract_youtube_video_id

EXPECTED_VIDEO_ID = "dQw4w9WgXcQ"
EXPECTED_PLAYLIST_ID = "PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4"


@pytest.mark.parametrize(
    "valid_video_url",
    [
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://youtube.com/watch?v=dQw4w9WgXcQ&list=PL123",
        "http://m.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://youtu.be/dQw4w9WgXcQ",
        "https://www.youtube.com/embed/dQw4w9WgXcQ",
        "dQw4w9WgXcQ",
    ],
)
def test_extract_youtube_video_id_accepts_valid_inputs(valid_video_url: str):
    parsed_id = extract_youtube_video_id(valid_video_url)
    assert parsed_id == EXPECTED_VIDEO_ID


@pytest.mark.parametrize(
    "invalid_video_input",
    [
        "http://localhost:8080/watch?v=dQw4w9WgXcQ",
        "http://127.0.0.1:8080/watch?v=dQw4w9WgXcQ",
        "http://169.254.169.254/latest/meta-data?v=dQw4w9WgXcQ",
        "http://10.0.0.1/watch?v=dQw4w9WgXcQ",
        "http://192.168.1.1/watch?v=dQw4w9WgXcQ",
        "file:///etc/passwd?v=dQw4w9WgXcQ",
        "http://attacker.com/watch?v=dQw4w9WgXcQ",
        "localhost",
        "internal",
        "not-a-video-id",
    ],
)
def test_extract_youtube_video_id_rejects_internal_addresses_and_non_youtube(
    invalid_video_input: str,
):
    parsed_id = extract_youtube_video_id(invalid_video_input)
    assert parsed_id is None


@pytest.mark.parametrize(
    "valid_playlist_url",
    [
        "https://www.youtube.com/playlist?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
        "https://youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
        "http://m.youtube.com/playlist?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
        "https://music.youtube.com/playlist?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
        "PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
    ],
)
def test_extract_youtube_playlist_id_accepts_valid_inputs(valid_playlist_url: str):
    parsed_id = extract_youtube_playlist_id(valid_playlist_url)
    assert parsed_id == EXPECTED_PLAYLIST_ID


@pytest.mark.parametrize(
    "invalid_playlist_input",
    [
        "http://localhost:8080/playlist?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
        "http://127.0.0.1:8080/playlist?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
        "http://169.254.169.254/?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
        "http://10.0.0.1/?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
        "http://192.168.0.1/?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
        "file:///etc/hosts?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
        "http://attacker.com/playlist?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
        "localhost",
        "internal",
    ],
)
def test_extract_youtube_playlist_id_rejects_internal_addresses_and_non_youtube(
    invalid_playlist_input: str,
):
    parsed_id = extract_youtube_playlist_id(invalid_playlist_input)
    assert parsed_id is None
