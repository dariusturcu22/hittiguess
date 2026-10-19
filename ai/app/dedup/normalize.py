import re
import unicodedata

NON_ALPHANUMERIC_PATTERN = re.compile(r"[^a-z0-9\s]")
REPEATED_WHITESPACE_PATTERN = re.compile(r"\s+")


def _strip_diacritics(text: str) -> str:
    decomposed_text = unicodedata.normalize("NFKD", text)
    return "".join(character for character in decomposed_text if not unicodedata.combining(character))


def normalize_artist_and_title(artist: str, title: str) -> str:
    """Collapses an artist/title pair into one normalized string for embedding
    comparison: lowercased, diacritics stripped, punctuation removed, and
    whitespace collapsed. Near-identical submissions that only differ in
    casing, accents, or punctuation land close together in embedding space
    once normalized this way, the same normalization shape already used for
    in-round guess matching (see docs/DECISIONS.md)."""
    combined_text = f"{artist} {title}".lower()
    text_without_diacritics = _strip_diacritics(combined_text)
    text_without_punctuation = NON_ALPHANUMERIC_PATTERN.sub(" ", text_without_diacritics)
    return REPEATED_WHITESPACE_PATTERN.sub(" ", text_without_punctuation).strip()
