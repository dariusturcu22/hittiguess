# 15. The one-year-cluster lock takes a year only one source supports

Branch: `fix/verification-lock-rule`. AI service only. Requires tasks 13 and 14. Files:
`ai/app/metadata/verification.py` (`_find_wikipedia_assisted_lock_year`, `evaluate_lock`),
`ai/tests/metadata/test_verification.py` (or the existing verification test file).

Read first: docs/DECISIONS.md "2026-09 | Metadata locks include corroborated Wikipedia and one-year source
clusters" (around line 943).

## What is wrong

The rule locks a song as `VERIFIED` when at least three of the four source years (MusicBrainz, Discogs,
Wikidata, Wikipedia) fall within one year, and takes the earliest. When the earliest year comes from a
single source, and that source picked up a promo, bootleg, or the original edition of an album the song
was only added to later, the song is locked one year too early. 68 production songs were locked this
way with disagreeing sources. Examples: Don't Let Me Down stored 2015 (Wikidata 2016, a Discogs bootleg
2015), Con Calma 2018 (Wikidata 2019, a Discogs promo compilation 2018), Nemo 2003 (Discogs and Wikidata
2004).

## What to build

1. Inside a one-year cluster, take the earliest year only when at least two sources report it; otherwise
   take the year the most sources report (ties go to the earlier year). The exact-majority path (three or
   more identical years) is unchanged.
2. Append a new dated entry to `docs/DECISIONS.md` describing the refinement and why (the file is
   append-only; never edit past entries).
3. A golden regression test of the whole decision, `evaluate_lock`, fed with production-shaped
   candidate lists recorded from the real sources after tasks 13 and 14 (store them as JSON fixtures),
   for at least these songs, with the expected stored year:

| Song | Expected |
|---|---|
| Hey Mama (David Guetta) | 2014 |
| La Isla Bonita (Madonna) | 1986 |
| Rude Boy (Rihanna) | 2009 |
| Moves Like Jagger (Maroon 5) | 2011 |
| Hips Don't Lie (Shakira) | 2006 |
| Burn (Ellie Goulding) | 2013 |
| Don't Let Me Down (The Chainsmokers) | 2016 |
| Con Calma (Daddy Yankee) | 2019 |
| Nemo (Nightwish) | 2004 |
| Timber (Pitbull) | 2013 |
| Heartbreak Hotel (C.C. Catch) | 1986 |
| How Deep Is Your Love (Calvin Harris) | 2015 |

The point of this test is that it starts from what production actually has (a YouTube title and a list
of main artists, no album), which the earlier spikes never did. Mock the Wikipedia extraction and
reconciliation LLM calls with fixed answers.
