import ipaddress
import re
import unicodedata
from urllib.parse import parse_qs, quote, urlsplit

# MusicBrainz, Wikidata, and Wikipedia all require a descriptive User-Agent
# identifying the application and a contact point; an unidentified or
# generic one risks a block. Wikidata and Wikipedia are the same Wikimedia
# infrastructure and share this same policy.
METADATA_SOURCE_USER_AGENT = "hittiguess/0.1 (+https://hittiguess.com; contact@hittiguess.com)"

YOUTUBE_ID_PATTERN = re.compile(r"^[a-zA-Z0-9_-]{11}$")
# Bare feat/ft markers match lowercase only: a capitalized "Ft." is a place-name
# abbreviation (Ft. Lauderdale), while the genuine trailing marker is lowercase.
# Parenthesized markers match either case.
FEATURED_ARTIST_SUFFIX_PATTERN = re.compile(
    r"\s*(?:\(\b(?:feat|ft)\b\.?\s+[^)]*\)|(?-i:\bfeat\b\.?\s+.*|\bft\b\.?\s+.*))$",
    re.IGNORECASE,
)

YOUTUBE_PLAYLIST_ID_MIN_LENGTH = 2
YOUTUBE_PLAYLIST_ID_MAX_LENGTH = 64
YOUTUBE_PLAYLIST_ID_PATTERN = re.compile(
    rf"^[a-zA-Z0-9_-]{{{YOUTUBE_PLAYLIST_ID_MIN_LENGTH},{YOUTUBE_PLAYLIST_ID_MAX_LENGTH}}}$"
)
ALLOWED_YOUTUBE_HOSTS = {
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
    "music.youtube.com",
    "youtu.be",
}
RESERVED_INTERNAL_HOSTS = {
    "localhost",
    "loopback",
    "local",
    "internal",
}
RESERVED_INTERNAL_HOST_SUFFIXES = (".localhost", ".local", ".internal")
ALLOWED_URL_SCHEMES = ("http", "https")

YOUTUBE_PLAYLIST_ITEMS_PAGE_SIZE = 50
# YouTube's videos.list endpoint accepts at most 50 comma-separated ids per call.
YOUTUBE_VIDEOS_BATCH_SIZE = 50

LUCENE_SPECIAL_CHARS = re.compile(r'([+\-!(){}\[\]^"~*?:\\&|/])')

ISO8601_DURATION_PATTERN = re.compile(
    r"^PT(?:(?P<hours>\d+)H)?(?:(?P<minutes>\d+)M)?(?:(?P<seconds>\d+)S)?$"
)
SECONDS_PER_HOUR = 3600
SECONDS_PER_MINUTE = 60


def is_internal_address_or_host(host_name: str | None) -> bool:
    if not host_name:
        return True
    normalized_host = host_name.strip().lower()
    if normalized_host in RESERVED_INTERNAL_HOSTS or normalized_host.endswith(
        RESERVED_INTERNAL_HOST_SUFFIXES
    ):
        return True
    try:
        parsed_ip = ipaddress.ip_address(normalized_host)
        return (
            parsed_ip.is_loopback
            or parsed_ip.is_private
            or parsed_ip.is_link_local
            or parsed_ip.is_unspecified
            or parsed_ip.is_multicast
        )
    except ValueError:
        return False


def extract_youtube_video_id(url: str | None) -> str | None:
    if not url:
        return None

    cleaned_url = url.strip()
    if is_internal_address_or_host(cleaned_url):
        return None

    if (
        "://" in cleaned_url
        or cleaned_url.startswith("//")
        or "/" in cleaned_url
        or "?" in cleaned_url
    ):
        try:
            parsed_uri = urlsplit(cleaned_url)
            if parsed_uri.scheme.lower() not in ALLOWED_URL_SCHEMES:
                return None
            host_name = parsed_uri.hostname
            if not host_name or is_internal_address_or_host(host_name):
                return None
            normalized_host = host_name.lower()
            if normalized_host not in ALLOWED_YOUTUBE_HOSTS:
                return None

            candidate: str | None = None
            if normalized_host == "youtu.be":
                path_segments = parsed_uri.path.lstrip("/").split("/")
                candidate = path_segments[0] if path_segments else None
            else:
                query_parameters = parse_qs(parsed_uri.query)
                watch_values = query_parameters.get("v")
                if watch_values:
                    candidate = watch_values[0]
                elif parsed_uri.path.startswith("/embed/"):
                    candidate = parsed_uri.path[len("/embed/") :].split("/")[0]
                elif parsed_uri.path.startswith("/v/"):
                    candidate = parsed_uri.path[len("/v/") :].split("/")[0]
                elif parsed_uri.path.startswith("/shorts/"):
                    candidate = parsed_uri.path[len("/shorts/") :].split("/")[0]

            return candidate if candidate and YOUTUBE_ID_PATTERN.match(candidate) else None
        except ValueError:
            return None

    return cleaned_url if YOUTUBE_ID_PATTERN.match(cleaned_url) else None


def extract_youtube_playlist_id(url_or_id: str | None) -> str | None:
    """Accepts either a bare playlist id or a full YouTube URL carrying a
    ?list=/&list= query parameter. A plain video URL with no list parameter
    (or any other non-matching input) returns None rather than guessing."""
    if not url_or_id:
        return None

    candidate = url_or_id.strip()
    if is_internal_address_or_host(candidate):
        return None

    if (
        "://" in candidate
        or candidate.startswith("//")
        or "/" in candidate
        or "?" in candidate
    ):
        try:
            parsed_uri = urlsplit(candidate)
            if parsed_uri.scheme.lower() not in ALLOWED_URL_SCHEMES:
                return None
            host_name = parsed_uri.hostname
            if not host_name or is_internal_address_or_host(host_name):
                return None
            normalized_host = host_name.lower()
            if normalized_host not in ALLOWED_YOUTUBE_HOSTS:
                return None

            query_parameters = parse_qs(parsed_uri.query)
            playlist_values = query_parameters.get("list")
            if playlist_values:
                playlist_id_candidate = playlist_values[0]
                return (
                    playlist_id_candidate
                    if YOUTUBE_PLAYLIST_ID_PATTERN.match(playlist_id_candidate)
                    else None
                )
            return None
        except ValueError:
            return None

    return candidate if YOUTUBE_PLAYLIST_ID_PATTERN.match(candidate) else None


def clean_youtube_text(text: str | None) -> str:
    if text is None:
        return ""
    cleaned = re.sub(r"(?i)\(.*official.*?\)", "", text)
    cleaned = re.sub(r"(?i)\[.*official.*?]", "", cleaned)
    cleaned = re.sub(r"(?i)(official|video|audio|lyric|lyrics|hd|4k|hq)", "", cleaned)
    cleaned = re.sub(r"(?i)(feat\.|ft\.|featuring).*", "", cleaned)
    cleaned = re.sub(r"[\u2013\u2014]", "-", cleaned)
    cleaned = re.sub(r"[()\[\]!?]", " ", cleaned)
    cleaned = cleaned.strip()
    cleaned = re.sub(r"\s{2,}", " ", cleaned)
    return cleaned


def strip_featured_artist_suffix(title: str) -> str:
    """Removes a trailing featured-artist annotation from a source query."""
    return FEATURED_ARTIST_SUFFIX_PATTERN.sub("", title).strip()


def escape_lucene(value: str | None) -> str:
    if not value:
        return ""
    return LUCENE_SPECIAL_CHARS.sub(r"\\\1", value)


def build_youtube_api_url(video_id: str, api_key: str) -> str:
    return (
        "https://www.googleapis.com/youtube/v3/videos"
        f"?part=snippet,contentDetails&id={quote(video_id)}&key={api_key}"
    )


def build_youtube_videos_batch_api_url(video_ids: list[str], api_key: str) -> str:
    joined_ids = ",".join(quote(video_id) for video_id in video_ids)
    return (
        "https://www.googleapis.com/youtube/v3/videos"
        f"?part=snippet&id={joined_ids}&key={api_key}"
    )


def build_youtube_playlist_items_api_url(
    playlist_id: str, api_key: str, page_token: str | None = None
) -> str:
    url = (
        "https://www.googleapis.com/youtube/v3/playlistItems"
        f"?part=contentDetails&playlistId={quote(playlist_id)}"
        f"&maxResults={YOUTUBE_PLAYLIST_ITEMS_PAGE_SIZE}&key={api_key}"
    )
    if page_token:
        url += f"&pageToken={quote(page_token)}"
    return url


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


def normalize_name(value: str | None) -> str:
    """Lowercases a name, removes accents, and turns punctuation into spaces so
    stylized and plain spellings compare as the same words."""
    decomposed = unicodedata.normalize("NFKD", value or "")
    without_diacritics = "".join(character for character in decomposed if not unicodedata.combining(character))
    without_punctuation = re.sub(r"[\W_]+", " ", without_diacritics)
    return re.sub(r"\s+", " ", without_punctuation).strip().lower()


def to_artist_list(artists: list[str]) -> list[str]:
    return [str(artist).strip() for artist in artists if str(artist).strip()]


def contains_name(text: str | None, name: str | None) -> bool:
    """Whether the normalized name appears in the normalized text as whole words, so a
    short name never matches inside a longer word."""
    normalized_name = normalize_name(name)
    if not normalized_name:
        return False
    return re.search(rf"(?<!\w){re.escape(normalized_name)}(?!\w)", normalize_name(text)) is not None
