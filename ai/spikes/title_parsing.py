"""Spike: title parsing helpers, extracting structured data from a raw
YouTube video title rather than just cleaning it down to a bare string.
See spikes/README.md.
"""

import re

FEATURED_ARTIST_PATTERN = re.compile(
    r"[\(\[]\s*(?:feat\.?|ft\.?|featuring)\s+(.+?)\s*[\)\]]",
    re.IGNORECASE,
)
ARTIST_SEPARATOR_PATTERN = re.compile(r"\s*(?:,|&|\band\b)\s*", re.IGNORECASE)


def extract_featured_artists(title: str) -> tuple[str, list[str]]:
    """Splits a "Title (feat. A, B & C)" style title into the clean title
    and a list of featured artist names, rather than just stripping the
    clause and discarding the names: MusicBrainz/Wikidata search also needs
    the clause gone to find the track at all (confirmed live, see TASKS.md's
    spike entry), but that's no reason to throw the names away too, story
    23's open question on multi-artist storage wants them kept."""
    match = FEATURED_ARTIST_PATTERN.search(title)
    if not match:
        return title.strip(), []

    clean_title = (title[: match.start()] + title[match.end() :]).strip()
    clean_title = re.sub(r"\s{2,}", " ", clean_title)

    featured_raw = match.group(1)
    featured_artists = [a.strip() for a in ARTIST_SEPARATOR_PATTERN.split(featured_raw) if a.strip()]
    return clean_title, featured_artists


if __name__ == "__main__":
    import sys

    if len(sys.argv) != 2:
        print("usage: title_parsing.py <title>")
        sys.exit(1)

    clean_title, featured = extract_featured_artists(sys.argv[1])
    print(f"clean title: {clean_title!r}")
    print(f"featured artists: {featured!r}")
