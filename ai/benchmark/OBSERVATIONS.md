# Year pipeline observations (2026-10-30)

Benchmark: `catalog.csv` holds 369 rows built from a production catalog export. Duplicates share a capture
file, so there are 357 distinct songs. 308 have a labelled year (labels by Claude from release history; notes
column says where a label is a judgment call). 61 Romanian, NCS, and other obscure songs are unlabelled and
need the owner's year. Every source's answer is recorded in `captures/<source>/`.

Run it again, from `ai/`:

    .venv/Scripts/python.exe -m benchmark.capture --source musicbrainz --songs benchmark/catalog.csv
    (same for discogs, wikidata, wikipedia; one process each)
    .venv/Scripts/python.exe -m benchmark.evaluate --songs benchmark/catalog.csv --misses production --unlabelled

## Score on the 308 labelled catalog songs

| Rule | Correct | Wrong | Left to reconciliation and review |
|---|---|---|---|
| production (merged in PR 346) | 293 | 4 | 11 |
| task 15 one-year cluster rule, same source answers | 256 | 4 | 48 |

The 65-song set in `songs.csv` scores 64 correct, 0 wrong, 1 left to review.

"Left to review" is not a wrong year: the song goes to the four-source reconciliation call and is stored
NEEDS_REVIEW instead of VERIFIED. These numbers replay the lock only; reconciliation's answers are not scored.

## The 4 wrong years

The four songs below get a wrong year. No Face No Name No Number (2000, the catalog video is the
re-recording), Feel Good (2016), and Limitless (2017) were wrong only because of labelling mistakes; the
sources were right and the labels are corrected.

Two against two, the rule takes the earlier year:
- What Is Love (Haddaway): MusicBrainz and Wikidata 1992, Discogs and Wikipedia 1993. Labelled 1993.
- Be My Lover (La Bouche): MusicBrainz and Wikidata 1994, Discogs and Wikipedia 1995. Labelled 1995.
  Both look like a late-year promo or regional first release; if the owner's rule counts those, the labels
  are wrong, otherwise the tie needs a tie-breaker. Tried on 2026-10-30: letting Wikipedia's side win a
  two-against-two tie fixes these two but breaks Hey Mama and Last Friday Night, where the earlier side is the
  album and Wikipedia states the later single (293 correct either way). Keeping the earlier year when an album
  that contains the song backs it gives 294, one more than production; which song it lost was not recorded.
  The rule is unchanged: one song gained on 308 does not justify an added special case, and ties are left to
  the admin drawer. See the 2026-10-30 entry in DECISIONS.md.

Two sources agree on a later year:
- Need You Now (Lady Antebellum): MusicBrainz and Discogs 2010, Wikipedia 2009, Wikidata nothing. Labelled
  2009, the single's year. Wikipedia extraction runs before the two-source rule, but 2009 is reported only by
  Wikipedia and has no second source, so the earliest supported year is MusicBrainz and Discogs' 2010.

Remix treated as the original:
- Macarena (Bayside Boys Remix): Discogs and Wikipedia matched the original 1993 Macarena; MusicBrainz and
  Wikidata say 1995. The sources are queried with the full title but match the original song. A remix that
  the catalog keeps as its own song needs its qualifier kept in the match.

## The 11 left to review

Only one source knows the song (reconciliation will probably store that year as NEEDS_REVIEW):
- Kyouran Hey Kids!!, High (JPB), Pill (Heuse), Safe & Sound (Different Heaven): MusicBrainz only.
- Brother Louie Mix '98: Discogs only. Unravel (TK from Ling tosite sigure): Wikipedia only.
- My Heart (Different Heaven, EH!DE): MusicBrainz 2014 (right), Discogs 2013 (another release).

Wrong matches:
- On & On (Cartoon): MusicBrainz 2019 and Discogs 2003 are other songs or later releases; only Wikipedia's
  2015 is right.

Identification problems, not year problems (the stored title or artist is wrong, so no source finds it):
- Your Idol: main artist stored as "Sony Pictures Animation" instead of Saja Boys.
- Around the World (La La La La La): artist stored as "A Touch Of Class"; the sources know the act as ATC.
  The duplicate song 252 is stored as ATC.
- Tukoh Taka: artist stored as "Keen - Remixes" instead of Nicki Minaj, Maluma, and Myriam Fares.

## Unlabelled songs: what the pipeline would do

Locks a year (owner to confirm): That's My Name 2009, Tenerife 2013, Aroma 2013, Could U 2012, Love in Brasil
2010, Fresh (Puya) 2009, LaLa Song 2011 (both copies), Tot Mai Sus 2011, Minim doi 2012 (MusicBrainz says 2014),
Onoare 2012, I Love You (OMFG) 2016, Shootin Stars 2014, Dreams (Lost Sky) 2018, Bad Boy (Cascada) 2004,
Da vina pe Voltaj 2012, Ecou 2013, Oarecare 2015, Vara nu dorm 2012, Vals 2017, Manifest 2010, Te Rog 2015,
Perfect Fără Tine 2013, Cât de departe 2012, Inevitabil va fi bine 2013, Acasă 2013, Pleacă 2010, Acele 2016,
In lipsa mea 2008, Gasca mea 2004.

Sources disagree, left to review: Desire (2009 vs 2011), My Passion (2011 vs 2010), Mi-e dor de noi (2010 vs
2011), Trag aer in piept (MusicBrainz 2023 vs Discogs 2007), Sweet Love (2012 vs 2011), Raisa (2006 vs 2005),
Oficial imi merge bine (2007 vs 2006), Caramelldansen (MusicBrainz 2021 vs Discogs 2008), Gura ta (2017 vs 2016),
Sus pe toc (2013 vs 2016, both copies), Avioane De Hartie (2015 vs 2016), Da-o Tare (2013 vs 2017),
Da, mama (2015 vs 2016).

One source only: Shining Heart 2012, I'm Sorry 2012, Samba 2009, Fa Muiere Taitei 2002, Turn It Up 2016,
C U Again 2016, Carolina 2000, Emotii 2014, Taxi 2014, Și îngerii au demonii lor 2015, Morsmordre 2020.

No source at all (the YouTube upload-year fallback from PR 346 applies): Coace Doamne prunele, Strazile din
Bucuresti, Tequila (Blacklist), Da-te-n dragostea mea, Te Pup Pa Pa!, 大展鴻圖 (Blueprint Supreme).

A pattern in the Romanian songs: MusicBrainz and Discogs often differ by one year, Discogs earlier. With
only two sources and no Wikidata or Wikipedia coverage, the rule cannot break the tie and the song goes to
review. More Romanian coverage would need another source.

## Possible next steps (not done)

1. Owner confirms the labels flagged above and the unlabelled years; then the catalog benchmark becomes a
   test like `tests/metadata/test_year_benchmark.py`.
2. Tie-breaker for two against two (What Is Love, Be My Lover): decide whether promos count, then encode it.
3. Keep remix and mix qualifiers in source matching for songs the catalog keeps as their own song.
4. Fix the three identification problems in the catalog (Your Idol, Around the World, Tukoh Taka) and look
   at why the precheck produced those artists.
5. Measure reconciliation too: the benchmark only scores the lock.

## Environment notes

- MusicBrainz throttled the capture heavily (2 to 3 requests per song, delays up to 10 s). Reset the local
  `stage_pace` row for MUSICBRAINZ and restart the capture if the delay sticks at 10 000 ms.
- Docker Desktop stopped twice during the work (stale `sailor-ingest.sock` once). Without the local database
  the pacing store falls back to 30 s waits per request.
- AI integration tests need `TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1` on Windows.
- The Wikidata "new device" email on 2026-10-29 came from this machine's AI service logging in with the bot
  account from `ai/.env` (Antigravity's fixture capture at 19:07).
