"""Third batch for calibrating the fast-tier MusicBrainz/Discogs traffic
split. Romanian pop, manele, and other regional/niche songs, rounding
out the mostly Western/mainstream mix the first two batches leaned on.
Ground truth researched via web search against each song's own
Wikipedia article or, where Wikipedia had nothing, multiple independent
platform listings (Spotify/Discogs/JioSaavn) agreeing on the same date.
Two entries (Hot, Mr. Saxobeat) are deliberately the single's release
year, not its later album's, a single-vs-album gap known to cause
mislabeling on mainstream platforms for these specific releases.
Wikidata is deliberately excluded, same reasoning as the second batch.
See spikes/README.md.

Manele coverage on Wikipedia is thin: most artists' pages list only a
hit-song rundown with no dated per-song discography, and several
otherwise-strong-looking song titles couldn't be pinned to a reliable
date and were left out. That thinness is itself a real data point for
how MusicBrainz/Discogs are likely to perform on this category, worth
noting when reading the results.
"""

# (title, artist, album, tier, note)
INTERNATIONAL_TEST_SONGS = [
    ("Hot", "Inna", "Hot", "romanian-pop", "single released 2008-08-12, a full year before the 2009 album of the same name, a single-vs-album gap known to cause mislabeling on streaming platforms for this artist"),
    ("Mr. Saxobeat", "Alexandra Stan", "Saxobeats", "romanian-pop", "single released 2010-09-12, the Saxobeats album followed in 2011, same single-vs-album gap as Hot"),
    ("Mor De Ochii Tai", "Florin Salam", None, "manele", "single, released 2015-05-22"),
    ("Jumatate Tu, Jumatate Eu", "Adrian Minune", None, "manele", "2003, one of his early-2000s breakout hits per his Wikipedia bio; no per-song discography page exists to cross-check against"),
    ("Essence", "Wizkid", "Made in Lagos", "afrobeats", "Nigerian afrobeats, released 2020-10-30, featuring Tems"),
    ("Envolver", "Anitta", "Versions of Me", "brazilian-reggaeton", "Brazilian artist, reggaeton not funk despite the genre reputation, released 2021-11-11"),
    ("Everyway That I Can", "Sertab Erener", None, "turkish-eurovision", "Turkey's winning 2003 Eurovision entry, released 2003-04-23"),
    ("Brown Munde", "AP Dhillon", None, "punjabi-independent", "independent Punjabi hip-hop, not Bollywood-affiliated, released 2020-09-18, credited to AP Dhillon, Gurinder Gill and Shinda Kahlon jointly"),
    ("Mafia", "Jala Brat", "Alfa & Omega", "balkan-trap", "Bosnian trap/turbofolk-adjacent, released 2018-04-15, featuring Buba Corelli"),
    ("Marionette", "Antonia", None, "romanian-pop", "2011, written by Afrojack; only a year, not an exact date, found across sources"),
    ("Todo de Ti", "Rauw Alejandro", "Vice Versa", "puerto-rican-reggaeton", "released 2021-05-20"),
    ("Migraine", "Moonstar88", "Todo Combo", "filipino-opm", "single released 2008-02-19, but the parent album Todo Combo came out in 2007, earlier; ground truth is the album year, per this project's own earliest-of-track/album rule, the single date alone is not the origin"),
]

GROUND_TRUTH = {
    "Hot": (2008,),
    "Mr. Saxobeat": (2010,),
    "Mor De Ochii Tai": (2015,),
    "Jumatate Tu, Jumatate Eu": (2003,),
    "Essence": (2020,),
    "Envolver": (2021,),
    "Everyway That I Can": (2003,),
    "Brown Munde": (2020,),
    "Mafia": (2018,),
    "Marionette": (2011,),
    "Todo de Ti": (2021,),
    "Migraine": (2007,),
}
