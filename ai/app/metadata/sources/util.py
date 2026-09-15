import re
from urllib.parse import quote

# MusicBrainz, Wikidata, and Wikipedia all require a descriptive User-Agent
# identifying the application and a contact point; an unidentified or
# generic one risks a block. Wikidata and Wikipedia are the same Wikimedia
# infrastructure and share this same policy.
METADATA_SOURCE_USER_AGENT = "hittiguess/0.1 (+https://hittiguess.com; contact@hittiguess.com)"

YOUTUBE_ID_PATTERN = re.compile(r"^[a-zA-Z0-9_-]{11}$")

LUCENE_SPECIAL_CHARS = re.compile(r'([+\-!(){}\[\]^"~*?:\\&|/])')

ISO8601_DURATION_PATTERN = re.compile(
    r"^PT(?:(?P<hours>\d+)H)?(?:(?P<minutes>\d+)M)?(?:(?P<seconds>\d+)S)?$"
)
SECONDS_PER_HOUR = 3600
SECONDS_PER_MINUTE = 60


def extract_youtube_video_id(url: str | None) -> str | None:
    if not url:
        return None

    url = url.strip()
    candidate: str | None = None

    if "youtu.be/" in url:
        candidate = url.rsplit("/", 1)[-1].split("?")[0].split("&")[0].split("#")[0]
    elif "youtube.com/watch" in url:
        v_index = url.find("v=")
        if v_index != -1:
            candidate = url[v_index + 2 :].split("&")[0]
    elif "youtube.com/embed/" in url:
        candidate = url.split("/embed/", 1)[1].split("?")[0].split("&")[0].split("#")[0]
    elif "youtube.com/v/" in url:
        candidate = url.split("/v/", 1)[1].split("?")[0].split("&")[0].split("#")[0]
    elif "youtube.com/shorts/" in url:
        candidate = url.split("/shorts/", 1)[1].split("?")[0].split("&")[0].split("#")[0]
    else:
        candidate = url

    return candidate if candidate and YOUTUBE_ID_PATTERN.match(candidate) else None


def clean_youtube_text(text: str | None) -> str:
    if text is None:
        return ""
    cleaned = re.sub(r"(?i)\(.*official.*?\)", "", text)
    cleaned = re.sub(r"(?i)\[.*official.*?]", "", cleaned)
    cleaned = re.sub(r"(?i)(official|video|audio|lyric|lyrics|hd|4k|hq)", "", cleaned)
    cleaned = re.sub(r"(?i)(feat\.|ft\.|featuring).*", "", cleaned)
    cleaned = re.sub(r"[–—]", "-", cleaned)
    cleaned = re.sub(r"[()\[\]!?]", " ", cleaned)
    cleaned = cleaned.strip()
    cleaned = re.sub(r"\s{2,}", " ", cleaned)
    return cleaned


def escape_lucene(value: str | None) -> str:
    if not value:
        return ""
    return LUCENE_SPECIAL_CHARS.sub(r"\\\1", value)


def build_youtube_api_url(video_id: str, api_key: str) -> str:
    return (
        "https://www.googleapis.com/youtube/v3/videos"
        f"?part=snippet,contentDetails&id={quote(video_id)}&key={api_key}"
    )


def parse_iso8601_duration_seconds(duration: str | None) -> int | None:
    """Converts YouTube's contentDetails.duration (ISO 8601, for example
    "PT3M52S") to whole seconds. Returns None when the value is missing or
    not in the hours/minutes/seconds form YouTube uses for video length."""
    if not duration:
        return None

    match = ISO8601_DURATION_PATTERN.match(duration)
    if match is None:
        return None

    hours = int(match.group("hours") or 0)
    minutes = int(match.group("minutes") or 0)
    seconds = int(match.group("seconds") or 0)
    return hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds
