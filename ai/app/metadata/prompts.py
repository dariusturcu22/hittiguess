"""Prompt builders for story 18's lock-or-LLM verification pipeline.

Adapted from ai/spikes/combo_prompts.py's validated implementations.
Two separate builders for two distinct LLM jobs: Wikipedia extraction
(reading comprehension over prose, runs on DeepSeek-V4-Flash) and
four-source reconciliation (structured candidate judgment, runs on
gpt-5-nano). Keeping them separate reflects the spike's finding that
one model isn't uniformly best at both tasks.
"""

_SHARED_TASK_INSTRUCTIONS = (
    "The response fields are enforced by a JSON schema, do not describe the JSON shape yourself."
)

# Without this rule, a reconciliation model can pick whichever single
# candidate's title most literally matches the song instead of the
# earliest year across that source's own full candidate list.
_EARLIEST_CANDIDATE_PER_SOURCE_RULE = (
    "- within a single source's own candidate list, that source's answer is the EARLIEST year among "
    "ALL of its candidates, not just whichever one candidate's title happens to match the song title "
    "most closely, a source can list many candidates (reissues, compilations, regional releases) and "
    "its true original is the earliest one, scan the whole list before deciding what that source says"
)

# A reconciliation model can carry an unstated prior toward trusting one
# source as inherently more authoritative than the others.
_NO_SOURCE_AUTHORITY_BIAS_RULE = (
    "- do not treat any one source, including MusicBrainz, as inherently more authoritative than the "
    "others by default, weigh how many candidates within and across sources actually agree on a year, "
    "not which source has the best general reputation"
)


def _format_musicbrainz_section(candidates: list[dict]) -> str:
    lines = ["=== MUSICBRAINZ DATA ==="]
    track_candidates = [candidate for candidate in candidates if candidate.get("query") == "track"]
    album_candidates = [candidate for candidate in candidates if candidate.get("query") == "album"]
    if not candidates:
        lines.append("(no candidates returned)")
    for label, group in (("Track query", track_candidates), ("Album query", album_candidates)):
        if not group:
            continue
        lines.append(f"{label}:")
        for candidate in group:
            lines.append(
                f"  - \"{candidate['title']}\" by {candidate['artist']} - "
                f"date: {candidate['date']} - type: {candidate['type']} - score: {candidate['score']}/100"
            )
    return "\n".join(lines)


def _format_discogs_section(candidates: list[dict]) -> str:
    lines = ["=== DISCOGS DATA ==="]
    track_candidates = [candidate for candidate in candidates if candidate.get("query") == "track"]
    album_candidates = [candidate for candidate in candidates if candidate.get("query") == "album"]
    if not candidates:
        lines.append("(no candidates returned)")
    for label, group in (("Track query", track_candidates), ("Album query", album_candidates)):
        if not group:
            continue
        lines.append(f"{label}:")
        for candidate in group:
            lines.append(f"  - master \"{candidate['title']}\" - year: {candidate['year']}")
    return "\n".join(lines)


def _format_wikidata_section(candidates: list[dict]) -> str:
    lines = ["=== WIKIDATA DATA ==="]
    if not candidates:
        lines.append("(no candidates returned)")
    for candidate in candidates:
        lines.append(
            f"  - [{candidate.get('query')}] {candidate.get('description')} - publication date: {candidate.get('date')}"
        )
    return "\n".join(lines)


def _format_wikipedia_extraction_section(extracted_year: int | None, confidence: str | None) -> str:
    """Wikipedia's contribution to reconciliation is a pre-extracted year,
    not raw article prose: the extraction step (reading comprehension) is
    kept separate from the reconciliation step, matching how every other
    source is reduced to a candidate year before reconciliation sees it."""
    if extracted_year is None:
        return "=== WIKIPEDIA (extracted year) ===\n(no year could be extracted from the article)"
    return f"=== WIKIPEDIA (extracted year) ===\n  - {extracted_year} (extraction confidence: {confidence})"


def build_wikipedia_extraction_prompt(title: str, artist: str, entries: list[dict]) -> str:
    """Tests reading comprehension over Wikipedia article prose. Answer is
    based solely on what the text states, not the model's memorized knowledge,
    since the extraction task is to test whether the article itself states
    the year, not to recall it from training.

    entries is a list of 0-2 dicts (one per "track"/"album" query) carrying
    the article's lead-section prose, matching the track+album comparison
    every other source already does."""
    if not entries:
        sections = "=== WIKIPEDIA ===\n(no article extract available)"
    else:
        sections = "\n\n".join(
            f"=== WIKIPEDIA ({entry['query']} article: {entry['page_title']!r}) ===\n{entry['extract']}"
            for entry in entries
        )
    return (
        "You are a music metadata analyst. Extract the ORIGINAL release year for THIS SPECIFIC "
        f"ARTIST'S version of this song ({artist}) from the Wikipedia article text below. Answer "
        "ONLY based on what this text states, even if you believe you know the answer from other "
        "knowledge, your training knowledge is not a source for this task, the point is to test "
        "whether the text itself states it.\n\n"
        f"Title: {title}\nArtist: {artist}\n\n"
        f"{sections}\n\n"
        "RULES:\n"
        "- release_year is the original release of THIS ARTIST'S version, never a reissue, remaster, "
        "chart-peak date, award date, or a different artist's earlier version of the same song\n"
        "- articles often cover a song's full history in one place: an earlier original by a "
        "different artist, a later cover that made it famous, live versions, remixes. If the text "
        "mentions more than one date for more than one artist, use the date that belongs to the "
        f"artist given above ({artist}), not the earliest date mentioned in the text, an earlier "
        "date for a DIFFERENT artist's version is not this answer\n"
        "- if both a track article and an album article are given, compare their dates and use "
        "whichever is earlier, that's the true original release regardless of which one is labeled "
        "the \"single\"\n"
        "- if none of the given text states a release year for this specific artist's version, "
        "release_year should be null, do not fill it in from anything you already know about this song\n"
        "- confidence: high if the text states the date plainly and unambiguously for this artist's "
        "version, medium if you had to infer it from indirect phrasing, low if you're genuinely "
        "unsure the text supports your answer\n\n"
        f"{_SHARED_TASK_INSTRUCTIONS}"
    )


def build_four_sources_prompt(
    title: str,
    artist: str,
    musicbrainz_candidates: list[dict],
    discogs_candidates: list[dict],
    wikidata_candidates: list[dict],
    wikipedia_extracted_year: int | None,
    wikipedia_extraction_confidence: str | None,
) -> str:
    return (
        "You are a music metadata analyst. Determine the correct ORIGINAL release year for this "
        "song using every source below.\n\n"
        f"Title: {title}\nArtist: {artist}\n\n"
        f"{_format_musicbrainz_section(musicbrainz_candidates)}\n\n"
        f"{_format_discogs_section(discogs_candidates)}\n\n"
        f"{_format_wikidata_section(wikidata_candidates)}\n\n"
        f"{_format_wikipedia_extraction_section(wikipedia_extracted_year, wikipedia_extraction_confidence)}\n\n"
        "RULES:\n"
        "- release_year is the song's original release, never a reissue, remaster, or compilation's date\n"
        f"{_EARLIEST_CANDIDATE_PER_SOURCE_RULE}\n"
        f"{_NO_SOURCE_AUTHORITY_BIAS_RULE}\n"
        "- If sources agree, that's your answer\n"
        "- If sources disagree, explain in your reasoning which one you trusted and why, don't just "
        "average or pick arbitrarily\n"
        "- If a source found nothing, say so, that's not the same as it disagreeing\n"
        "- If every source came up empty or unusable, release_year should be null, don't invent a year\n"
        "- confidence: high if sources agree or the disagreement is trivially resolved, medium if you "
        "had to make a real judgment call between conflicting sources, low if the data is too thin or "
        "contradictory to be confident\n\n"
        f"{_SHARED_TASK_INSTRUCTIONS}"
    )


def build_title_artist_extraction_prompt(video_title: str, channel_title: str) -> str:
    """Splits a raw YouTube video title and channel name into a clean song
    title and artist, run before any structured source is queried so those
    lookups search on a name a source can actually match rather than a raw
    upload title. Validated against spikes/extraction_test_set.py's ten
    adversarial cases: typos, reversed artist/title order, no separator at
    all, decorative unicode, a YouTube auto-generated "- Topic" channel
    suffix, and a genuinely unidentifiable submission."""
    return (
        "You are a music metadata analyst. Extract the song's real TITLE and ARTIST from this raw "
        "YouTube video title and channel name.\n\n"
        f"Video title: {video_title!r}\nChannel name: {channel_title!r}\n\n"
        "RULES:\n"
        "- the video title is very often formatted \"Artist - Title\", split it into the two fields, "
        "don't leave the artist name sitting inside the title text\n"
        "- artist and title can appear in EITHER order with no separator at all, use your own "
        "knowledge of real songs to tell which part is the artist and which is the title\n"
        "- the channel name is a hint, not the answer: an official artist channel usually matches, but "
        "a channel name ending in \"- Topic\" (YouTube's auto-generated music channels) or \"VEVO\" is "
        "still that artist's name with the suffix removed, and an unrelated channel (a compilation "
        "upload, a lyrics channel) may not name the artist at all\n"
        "- REMOVE: 'Remastered', 'Remaster', 'HD', 'HQ', '4K', 'Official Video', 'Official Audio', "
        "'Lyrics', 'Lyric Video', 'Live', 'Live Version', 'Radio Edit', 'Single Version', year "
        "qualifiers like '2019 Remaster', and decorative symbols or emoji around the name\n"
        "- KEEP: 'Remix', 'Mashup', 'Original Mix', 'Extended Mix', they identify a specific version\n"
        "- a featured-artist credit ('feat. X', 'ft. X', 'featuring X') stays part of the title text "
        "exactly as it would in the cleaned song title, do not merge it into the artist field or drop it\n"
        "- fix an obvious typo in the title or artist when you're confident of the real name (e.g. a "
        "misspelled song or artist name), but don't fix a stylized or intentionally unusual real name\n"
        "- if the raw text genuinely does not identify a real song (a mixtape label, a generic track "
        "number, gibberish), title and artist should both be null, do not invent a plausible-sounding "
        "answer just because one is expected\n"
        "- confidence: high if the split is unambiguous, medium if you had to infer artist/title order "
        "or resolve a typo, low if you're genuinely unsure of the split or the identification itself\n\n"
        f"{_SHARED_TASK_INSTRUCTIONS}"
    )
