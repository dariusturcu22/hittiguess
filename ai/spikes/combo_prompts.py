"""Prompt builders for the three LLM-combination scenarios: LLM+MusicBrainz
only, LLM+Discogs only, and all three structured sources combined. Reads
from response_cache.py's cached candidate data rather than making a live
API call. See spikes/README.md.
"""

_SHARED_TASK_INSTRUCTIONS = (
    "The response fields are enforced by a JSON schema, do not describe the JSON shape yourself."
)

# Without this rule, a reconciliation model can pick whichever single
# candidate's title most literally matches the song instead of the
# earliest year across that source's own full candidate list, then
# dismiss a source with a correct but non-matching-title candidate as
# unreliable. Each source's own deterministic code already takes the
# earliest year among its own candidates; the reconciliation prompt needs
# to say so explicitly too, rather than leaving the model to guess which
# single candidate represents that source's answer.
_EARLIEST_CANDIDATE_PER_SOURCE_RULE = (
    "- within a single source's own candidate list, that source's answer is the EARLIEST year among "
    "ALL of its candidates, not just whichever one candidate's title happens to match the song title "
    "most closely, a source can list many candidates (reissues, compilations, regional releases) and "
    "its true original is the earliest one, scan the whole list before deciding what that source says"
)

# A reconciliation model can carry an unstated prior toward trusting one
# source, typically MusicBrainz, as inherently more authoritative than
# the others, and lean on that prior even when it contradicts the actual
# candidate evidence. Nothing in this prompt should imply that ranking.
_NO_SOURCE_AUTHORITY_BIAS_RULE = (
    "- do not treat any one source, including MusicBrainz, as inherently more authoritative than the "
    "others by default, weigh how many candidates within and across sources actually agree on a year, "
    "not which source has the best general reputation"
)


def _format_musicbrainz_section(candidates: list[dict]) -> str:
    lines = ["=== MUSICBRAINZ DATA ==="]
    track_candidates = [candidate for candidate in candidates if candidate["query"] == "track"]
    album_candidates = [candidate for candidate in candidates if candidate["query"] == "album"]
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
    track_candidates = [candidate for candidate in candidates if candidate["query"] == "track"]
    album_candidates = [candidate for candidate in candidates if candidate["query"] == "album"]
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
            f"  - [{candidate['query']}] {candidate.get('description')} - publication date: {candidate['date']}"
        )
    return "\n".join(lines)


def build_musicbrainz_only_prompt(title: str, artist: str, musicbrainz_candidates: list[dict]) -> str:
    return (
        "You are a music metadata analyst. Determine the correct ORIGINAL release year for this "
        "song using the MusicBrainz data below, nothing else.\n\n"
        f"Title: {title}\nArtist: {artist}\n\n"
        f"{_format_musicbrainz_section(musicbrainz_candidates)}\n\n"
        "RULES:\n"
        "- release_year is the song's original release, never a reissue, remaster, or compilation's date\n"
        "- If MusicBrainz returned both a track-level and an album-level candidate, prefer the earlier "
        "date, unless there's a clear reason the track postdates the album (for example, it's a later "
        "single not from that album)\n"
        f"{_EARLIEST_CANDIDATE_PER_SOURCE_RULE}\n"
        "- If MusicBrainz returned nothing usable, or only a candidate that clearly isn't this song, "
        "say so, release_year should be null, don't invent a year\n"
        "- confidence: high if MusicBrainz's data directly and unambiguously answers it, medium if you "
        "had to reconcile between multiple candidates, low if you're extrapolating beyond what "
        "MusicBrainz actually gave you\n\n"
        f"{_SHARED_TASK_INSTRUCTIONS}"
    )


def build_discogs_only_prompt(title: str, artist: str, discogs_candidates: list[dict]) -> str:
    return (
        "You are a music metadata analyst. Determine the correct ORIGINAL release year for this "
        "song using the Discogs data below, nothing else.\n\n"
        f"Title: {title}\nArtist: {artist}\n\n"
        f"{_format_discogs_section(discogs_candidates)}\n\n"
        "RULES:\n"
        "- release_year is the song's original release, never a reissue, remaster, or compilation's date\n"
        "- If Discogs returned both a track-level and an album-level candidate master, prefer the "
        "earlier year, unless there's a clear reason the track postdates the album (for example, it's "
        "a later single not from that album)\n"
        f"{_EARLIEST_CANDIDATE_PER_SOURCE_RULE}\n"
        "- If Discogs returned nothing usable, or only a candidate that clearly isn't this song, say "
        "so, release_year should be null, don't invent a year\n"
        "- confidence: high if Discogs' data directly and unambiguously answers it, medium if you had "
        "to reconcile between multiple candidate masters, low if you're extrapolating beyond what "
        "Discogs actually gave you\n\n"
        f"{_SHARED_TASK_INSTRUCTIONS}"
    )


def build_wikipedia_extraction_prompt(title: str, artist: str, entries: list[dict]) -> str:
    """Tests reading comprehension over prose, not recall or reconciliation
    among structured candidates, a genuinely different skill from every
    other combination prompt in this file. The instruction to answer only
    from the given text, even overriding what the model already "knows",
    is deliberate: without it, a model with strong memorized knowledge of
    the song would just recall the answer regardless of whether the text
    actually states it, and the test would silently measure memory again
    instead of extraction.

    entries is response_cache's cached list for this song (0-2 dicts, one
    per "track"/"album" query, see run_full_wikipedia.py), the same
    track-vs-album comparison MusicBrainz, Discogs, and Wikidata already
    do; Wikipedia was the one source missing it, since it used to only
    ever look up the song's own article.

    A Wikipedia article covering a song's full history can state a date
    for an original artist's earlier recording and a separate date for a
    later cover in the same short paragraph; the cover-attribution rule
    below exists to keep extraction pinned to the specific artist being
    asked about instead of whichever date happens to be mentioned
    first."""
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


def _format_wikipedia_extraction_section(extracted_year: int | None, confidence: str | None) -> str:
    """Wikipedia's contribution here is a pre-extracted year, not raw
    article prose: the extraction step (reading comprehension, decided to
    run on DeepSeek-V4-Flash, see spikes/README.md) is kept separate from
    this reconciliation step, the same two-stage split already used for
    every other source (each is reduced to a candidate year before
    reconciliation ever sees it), rather than handing raw prose to the
    reconciliation model and asking it to do both jobs in one pass."""
    if extracted_year is None:
        return "=== WIKIPEDIA (extracted year) ===\n(no year could be extracted from the article)"
    return f"=== WIKIPEDIA (extracted year) ===\n  - {extracted_year} (extraction confidence: {confidence})"


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


def build_all_sources_prompt(
    title: str,
    artist: str,
    musicbrainz_candidates: list[dict],
    discogs_candidates: list[dict],
    wikidata_candidates: list[dict],
) -> str:
    return (
        "You are a music metadata analyst. Determine the correct ORIGINAL release year for this "
        "song using every source below.\n\n"
        f"Title: {title}\nArtist: {artist}\n\n"
        f"{_format_musicbrainz_section(musicbrainz_candidates)}\n\n"
        f"{_format_discogs_section(discogs_candidates)}\n\n"
        f"{_format_wikidata_section(wikidata_candidates)}\n\n"
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
