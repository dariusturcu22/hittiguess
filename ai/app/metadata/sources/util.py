import re
from urllib.parse import quote

YOUTUBE_ID_PATTERN = re.compile(r"^[a-zA-Z0-9_-]{11}$")

RELEASE_DATE_PATTERNS = [
    re.compile(r"released on ([A-Za-z]+ \d{1,2}, \d{4})"),
    re.compile(r"released in ([A-Za-z]+ \d{4})"),
    re.compile(r"released (\d{4})"),
    re.compile(r"(\d{4}) single"),
    re.compile(r"(\d{4}) song"),
]

LUCENE_SPECIAL_CHARS = re.compile(r'([+\-!(){}\[\]^"~*?:\\&|/])')


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


def extract_release_date_from_text(text: str | None) -> str:
    if not text:
        return "No release date found"

    for pattern in RELEASE_DATE_PATTERNS:
        match = pattern.search(text)
        if match:
            return f"Released: {match.group(1)}"

    return "No release date found"


def escape_lucene(value: str | None) -> str:
    if not value:
        return ""
    return LUCENE_SPECIAL_CHARS.sub(r"\\\1", value)


def build_youtube_api_url(video_id: str, api_key: str) -> str:
    return (
        "https://www.googleapis.com/youtube/v3/videos"
        f"?part=snippet,contentDetails&id={quote(video_id)}&key={api_key}"
    )


def build_musicbrainz_api_url(query: str) -> str:
    return f"https://musicbrainz.org/ws/2/recording/?query={quote(query)}&fmt=json&limit=10"


def build_wikipedia_search_url(search_term: str) -> str:
    return (
        "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch="
        f"{quote(search_term)}&format=json&srlimit=3"
    )


def build_wikipedia_page_url(page_title: str) -> str:
    return (
        f"https://en.wikipedia.org/w/api.php?action=query&titles={quote(page_title)}"
        "&prop=extracts&exintro=true&format=json"
    )
