"""Story 20's second test dimension: extracting a clean, correct title and
artist from a messy raw submission, independent of source-API reconciliation
(that's run_matrix.py's job). No source lookups happen here, this is purely
whether an LLM candidate can apply the same cleaning judgment already
encoded in the production prompt (app/metadata/prompt.py's TITLE CLEANING
RULES): strip upload-artifact noise, keep remix/feat credits, and, for the
deliberately unanswerable case, resist inventing an answer rather than
guessing with false confidence.

Not run automatically, see spikes/README.md for how a candidate client
consumes this list.
"""


class ExtractionTestCase:
    def __init__(self, raw_submission: str, expected_title: str | None, expected_artist: str | None, note: str):
        self.raw_submission = raw_submission
        self.expected_title = expected_title
        self.expected_artist = expected_artist
        self.note = note


# expected_title/expected_artist are None only for the deliberately
# unanswerable case, where the correct behavior is low confidence and no
# invented answer, not a specific string to match.
EXTRACTION_TEST_CASES = [
    ExtractionTestCase(
        "Bohemain Rapsody - Qeen",
        "Bohemian Rhapsody",
        "Queen",
        "typo'd title and artist, tests recognition through misspelling",
    ),
    ExtractionTestCase(
        "Big In Japan (2019 Remaster) [Official Video]",
        "Big In Japan",
        "Alphaville",
        "remaster tag and official-video suffix, matches prompt.py's own worked example",
    ),
    ExtractionTestCase(
        "SMELLS LIKE TEEN SPIRIT (HQ AUDIO) - Nirvana - Topic",
        "Smells Like Teen Spirit",
        "Nirvana",
        "all-caps title plus a YouTube auto-generated \"- Topic\" channel suffix on the artist",
    ),
    ExtractionTestCase(
        "blinding lights ofenbach remix - The Weeknd",
        "Blinding Lights (Ofenbach Remix)",
        "The Weeknd",
        "remix credit must be KEPT per the cleaning rules, not stripped like Remastered/HD would be",
    ),
    ExtractionTestCase(
        "Somebody That I Used To Know - Live at Glastonbury 2012",
        "Somebody That I Used To Know",
        "Gotye",
        "live-performance qualifier must be REMOVED per the cleaning rules; artist only implied by context, not stated",
    ),
    ExtractionTestCase(
        "Rihanna ft. Jay-Z - Umbrella (Lyrics)",
        "Umbrella",
        "Rihanna",
        "featured-artist credit must be KEPT in a separate field per story 23's multi-artist open question, not merged or dropped; (Lyrics) suffix stripped",
    ),
    ExtractionTestCase(
        "☆Palm Mall☆ - 猫 シ Corp [FULL ALBUM]",
        "Palm Mall",
        "猫 シ Corp",
        "decorative unicode stars and a [FULL ALBUM] suffix around a real stylized artist name",
    ),
    ExtractionTestCase(
        "roygbiv boc",
        "Roygbiv",
        "Boards of Canada",
        "heavily abbreviated artist initials, tests recognition without the full name present at all",
    ),
    ExtractionTestCase(
        "Boards of Canada Roygbiv HD",
        "Roygbiv",
        "Boards of Canada",
        "artist and title given in reversed order with no separator, tests whether the swap is caught",
    ),
    ExtractionTestCase(
        "DJ Mixtape Vol 3 track 7",
        None,
        None,
        "genuinely unidentifiable, correct behavior is low confidence and no invented title/artist, not a guess",
    ),
]
