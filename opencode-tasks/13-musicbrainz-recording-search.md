# 13. MusicBrainz misses multi-artist songs and never checks the album

Branch: `fix/musicbrainz-recording-search`. AI service only. Files:
`ai/app/metadata/sources/musicbrainz.py`, `ai/app/metadata/service.py` (`_source_query`,
`run_structured_source_stage`), `ai/tests/metadata/test_sources_musicbrainz.py`.

Read first: docs/DECISIONS.md entry "2026-09 | Metadata pipeline call/reconcile shape" (around line 193).
It requires, for each source, querying the track AND its parent album and taking the earliest year.

## What is wrong (production data: the 319 songs that have source findings)

1. Artist phrase. `_source_query` joins all main artists into one string, `" & ".join(main_artists)`,
   and `search_release_group` puts it in an exact phrase:
   `releasegroup:"Timber" AND artist:"Pitbull & Kesha"`. MusicBrainz credits that release as
   "Pitbull feat. Kesha", so the phrase matches nothing. Result: MusicBrainz returned no year for 55 of
   101 multi-artist songs (54%), against 24 of 218 single-artist songs (11%). Examples with empty
   results: Timber, Work, The Monster, Bang Bang, Yeah!, What's My Name?, Moves Like Jagger,
   Hips Don't Lie, Don't Let Me Down, Con Calma.
2. No album. `run_structured_source_stage` calls `search_or_raise(title, artists)` without an album,
   so the album half of the rule never runs. YouTube identification produces no album name. Example:
   "Hey Mama" is on the album "Listen" (2014-11-21) and was also released as a single in 2015; the rule
   says 2014, production stored 2015.
3. Top-score selection unused. `select_best_release_group` (scan only the release groups tied at the top
   score, prefer the matching type, take the earliest date) is implemented and tested but never called.
   `_candidates_from_groups` keeps the first 5 release groups at any score, and
   `verification._extract_earliest_year` takes the minimum over all of them, so unrelated low-score
   groups set the year. Examples: "Desire" (Morris) got 1951, "Believe" (Cher) 1976, "Try" (P!nk) 2003.

## What to build

1. Artist matching: query with the FIRST main artist only (`artist:"Pitbull"`), then keep a candidate only
   if its artist credit contains at least one of the song's main artists (compare lowercase, accents and
   punctuation stripped, so "P!nk" matches "P!nk" and "Beyoncé" matches "Beyonce"). Change `_source_query`
   to return the list of main artists, and update the other sources' calls to pass what they need
   (Discogs and Wikidata are task 14; for now pass them the same joined string they get today).
2. Track candidates: only release groups whose score equals the top score among artist-matched groups
   (reuse `select_best_release_group`'s logic, but return every top-scored, artist-matched group as a
   candidate so reconciliation still sees them).
3. Album candidates: find the parent albums through a recording search,
   `https://musicbrainz.org/ws/2/recording?query=recording:"<title>" AND artist:"<first artist>"&fmt=json&limit=50`.
   Keep recordings with score 90 or more whose artist credit matches (as in step 1) and whose
   disambiguation does not contain live, remix, acoustic, instrumental, karaoke, demo, or edit. From their
   `releases`, keep official releases whose release group has primary type Album or EP and no secondary
   type Compilation, Live, Remix, DJ-mix, Mixtape/Street, Soundtrack (keep Soundtrack only when the
   recording is by that artist), or Demo. Each distinct release group becomes one candidate with
   `query: "album"` and the earliest date among the releases that contain this recording. Use those
   release dates, NOT the release group's `first-release-date`: a song added to a later re-release of an
   album ("Moves Like Jagger" on the 2011 "Hands All Over" re-release, "Hips Don't Lie" on the 2006
   "Oral Fixation Vol. 2" reissue) would otherwise get the original edition's year, which predates the
   song. Keep at most 5.
4. Respect the existing MusicBrainz pacing (one request per second is already handled in the source
   client; the album search adds one request per song).
5. Append a new dated entry to `docs/DECISIONS.md` (the file is append-only; never edit past entries): the parent album is found through the recording search
   because YouTube gives no album name; release-group search is still used for the track itself.
6. Fix the docstring of `verification._extract_earliest_year`, which claims each source already runs a
   track and album query.

## Tests (use `respx`, already a dev dependency, with JSON fixtures saved under `ai/tests/metadata/fixtures/musicbrainz/`)

Record real responses once (run the queries by hand, save the JSON) for these songs and assert the
earliest year:

| Song | Main artists | Expected |
|---|---|---|
| Hey Mama | David Guetta, Nicki Minaj, Bebe Rexha, Afrojack | 2014 (album Listen) |
| Timber | Pitbull, Kesha | 2013 |
| Moves Like Jagger | Maroon 5, Christina Aguilera | 2011 |
| Desire | Morris | 2009 (not 1951) |
| Try | P!nk | 2012 (not 2003) |

Also a test that `run_structured_source_stage` passes a list of main artists end to end.
