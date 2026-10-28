# Year audit of the production catalog (2026-10-28)

The production catalog holds 370 songs. The songs below break the documented year rule: the earliest
year across the track and the album it originally appeared on, where a remix, cover, or later version
is its own song. The counts at the end come from each song's stored per-source findings.

Treat this list as the expected result of task 16's recheck, not as data to write blindly: confirm each
proposal in the admin drawer.

## Verified songs with a wrong year

| Song id | Song | Stored | Correct | Why |
|---|---|---|---|---|
| 43 | Hey Mama (David Guetta) | 2015 | 2014 | On the album Listen, Nov 2014; the 2015 single is later |
| 30 | La Isla Bonita (Madonna) | 1987 | 1986 | On True Blue, Jun 1986 |
| 14 | Rude Boy (Rihanna) | 2010 | 2009 | On Rated R, Nov 2009 |
| 48 | Burn (Ellie Goulding) | 2012 | 2013 | Released Jul 2013 on Halcyon Days; the 2012 Halcyon edition does not include it |
| 130 | Moves Like Jagger (Maroon 5) | 2010 | 2011 | Released Jun 2011; added to the 2011 re-release of Hands All Over |
| 97 | Hips Don't Lie (Shakira) | 2005 | 2006 | Released Feb 2006 on the Oral Fixation Vol. 2 reissue |
| 317 | Beautiful Liar (Beyoncé, Shakira) | 2006 | 2007 | Released Feb 2007 on the B'Day deluxe edition |
| 124 | Don't Let Me Down (The Chainsmokers) | 2015 | 2016 | Released Feb 2016; the 2015 Discogs entry is a bootleg |
| 183 | Con Calma (Daddy Yankee, Snow) | 2018 | 2019 | Released Jan 2019; the 2018 Discogs entry is a promo compilation |
| 199 | Nemo (Nightwish) | 2003 | 2004 | Single Apr 2004, album Once Jun 2004 |

## Needs-review songs with a wrong year

| Song id | Song | Stored | Correct | Why |
|---|---|---|---|---|
| 339 | Heartbreak Hotel (C.C. Catch) | 1956 | 1986 | Wikidata matched Elvis Presley's song |
| 336 | Inferno (Mrs. GREEN APPLE) | 1987 | 2019 | Fire Force opening, 2019 |
| 214 | How Deep Is Your Love (Calvin Harris, Disciples) | 1977 | 2015 | Wikidata matched the Bee Gees song |
| 276 | Tequila (Blacklist, Carla's Dreams) | 1958 | check, Discogs says 2019 | The Champs' 1958 song was matched |
| 32 | Move Your Body (Sia) | 2017 | 2016 | On This Is Acting, Jan 2016; the 2017 single is later |
| 140 | Brother Louie Mix '98 (Modern Talking) | 1986 | 1998 | The '98 mix is its own song (multi-artist decision: remixes split) |

## Songs with no year

334 Kyouran Hey Kids!! (THE ORAL CIGARETTES, 2015), 330 大展鴻圖 (Blueprint Supreme), 177 Coace Doamne
prunele, 240 C U Again, 222 Why We Lose. The last four need a manual year.

## Identification errors (wrong artist)

| Song id | Song | Stored artist | Correct artist |
|---|---|---|---|
| 315 | Tukoh Taka | Keen - Remixes | Nicki Minaj, Maluma, Myriam Fares |
| 54 | Your Idol | Sony Pictures Animation | Saja Boys |
| 264 | Caramelldansen | Caramella Girls | check: the 2001 original is by Caramell; the Caramella Girls version is later |

## Duplicates (task 17)

4 Minutes (10, 40), Say It Right (21, 348), Rhythm Is a Dancer (33, 337), Bleeding Love (41, 344),
Sun Is Up (57, 121), Stereo Love (58, 100), Pump It (103, 186), Judas (136, 322), LaLa Song (188, 191),
Sus pe toc (277, 295), 365 (316, 352), Kings & Queens (326, 358).

## Counts behind the pipeline tasks

- 319 songs have source findings; 50 (ids 1 to 50) predate pipeline tracking and have none.
- MusicBrainz returned no year for 55 of 101 multi-artist songs and 24 of 218 single-artist songs.
- Wikidata's chosen entity was not a song or single for 28 songs.
- Discogs candidates included a "Various" compilation for 68 songs.
- 68 songs were locked by the one-year-cluster rule while their sources disagreed.
- 114 songs are `NEEDS_REVIEW`; most are songs where MusicBrainz found nothing or Wikidata matched the
  wrong entity, so the sources disagreed and the reconciliation result goes to review by design.
