# 14. Wikidata and Discogs accept other artists' works, compilations, and non-songs

Branch: `fix/source-matching`. AI service only. Requires task 13 (main artists arrive as a list).
Files: `ai/app/metadata/sources/wikidata.py`, `ai/app/metadata/sources/discogs.py`, their tests.

Read first: docs/DECISIONS.md "2026-09 | Metadata pipeline call/reconcile shape", the Wikidata and
Discogs bullets.

## What is wrong (production source findings)

Wikidata, `pick_best_match(matches, artist)`:
- It looks for the artist string in each result's description, but the string is all main artists
  joined ("Rihanna & Drake"), so multi-artist songs never match by artist.
- The fallback accepts any description containing `_MUSIC_DESCRIPTION_KEYWORDS =
  ("single", "song", "album", "track", " ep", "recording", "record")`. "record" matches "record label",
  and "album"/" ep" accept other artists' albums. 28 production songs resolved to a non-song entity, for
  example: What's My Name? -> "2017 EP by T-ara" (2017), Yeah! -> "album by Brownsville Station" (1973),
  Fresh -> "album by Sly and the Family Stone" (1973), Timber -> "instrumental track", Work and The
  Monster -> "American record label/producer", Bang Bang (Jessie J) -> "original song written ... 1966".
  The sitelinks count, which drives difficulty, comes from the same wrong entity.

Discogs, `search_release(title, artist)`:
- Free-text search `q = f"{artist} {title}"` with no artist check on the results, so "Cheap Thrills"
  (Sia) picked up Big Brother and the Holding Company's 1968 album, "Safe & Sound" got 1993, "Dreams"
  (Lost Sky) got 1960.
- `masterless_release_years` adds the `year` of releases without a master as candidates. That decision
  is not in DECISIONS.md, and it lets compilations ("Various - ...", 68 songs have one), bootlegs, and
  promos into the earliest-year minimum (for example a 2018 "Various - DMC" promo for Con Calma, 2019).

## What to build

Wikidata:
1. Match the artist per main artist: a result matches if its description contains any main artist
   (normalized as in task 13).
2. Fallback: only descriptions that say "song" or "single" (not album, EP, record, track, recording),
   and only when the description names no other artist ("by X" where X is not a main artist rejects it).
3. When a match is found, confirm it with the entity's performer claim (P175) when present: at least one
   performer label must match a main artist, otherwise reject and try the next result. Fetching the
   claims already happens in `_resolve_candidate`; reuse it.

Discogs:
1. Use the structured search parameters `artist=<first main artist>&track=<title>&type=release` instead
   of free text `q`, and keep a release only when its title starts with one of the main artists
   ("Artist - Title" is Discogs' title format) or its `artist` field matches.
2. Drop releases whose title starts with "Various" and releases with format "Compilation" or
   "Unofficial Release" (the search result's `format` list).
3. Keep masterless candidates only when no master-based candidate exists for the song, and describe
   that rule in a new dated entry appended to `docs/DECISIONS.md` (append-only; never edit past entries).

## Tests (respx with recorded JSON fixtures under `ai/tests/metadata/fixtures/`)

| Song | Source | Expected |
|---|---|---|
| What's My Name? (Rihanna, Drake) | Wikidata | the 2010 Rihanna song, not the T-ara EP |
| Yeah! (Usher, Lil Jon, Ludacris) | Wikidata | 2004 song, not the Brownsville Station album |
| Bang Bang (Jessie J, Ariana Grande, Nicki Minaj) | Wikidata | 2014, not 1966 |
| Cheap Thrills (Sia) | Discogs | no 1968 candidate |
| Con Calma (Daddy Yankee, Snow) | Discogs | earliest 2019, no "Various" candidate |
