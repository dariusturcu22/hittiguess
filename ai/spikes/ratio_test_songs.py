"""Second batch for calibrating the fast-tier MusicBrainz/Discogs traffic
split. Six new adversarial cases (categories not yet covered by
run_matrix.py's set: cover-attribution via a more famous cover, a
traditional/disputed-origin folk song, soundtrack attribution, an older
jazz/instrumental era, a pre-1950s foreign-market release, and a non-English
2010s hit) plus twelve ordinary, well-established songs, since the fast
tier mostly sees typical requests, not worst-case ones, and a ratio derived
only from adversarial cases would skew toward whatever handles chaos best,
not what handles a normal playlist best. Wikidata is deliberately excluded
from this round, already established as the least reliable and slowest to
answer of the three; this batch is purely for weighing MusicBrainz against
Discogs. See spikes/README.md.
"""

# (title, artist, album, tier, note)
RATIO_TEST_SONGS = [
    # --- adversarial: new categories ---
    ("Hallelujah", "Jeff Buckley", "Grace", "cover-attribution", "1994 cover; Leonard Cohen's 1984 original is also widely indexed"),
    ("House of the Rising Sun", "The Animals", None, "disputed-origin", "a traditional folk song of disputed ultimate authorship, no single canonical 'original' the way a written song has"),
    ("My Heart Will Go On", "Celine Dion", "Let's Talk About Love", "soundtrack-attribution", "1997 single tied to the Titanic soundtrack, risk of a source dating it to the film rather than the single"),
    ("Take Five", "Dave Brubeck", "Time Out", "era-diversity", "1959 jazz instrumental, oldest and only instrumental entry in either batch"),
    ("La Vie en rose", "Édith Piaf", None, "era-diversity", "1947 French release, oldest and only pre-1950s entry, non-English"),
    ("Gangnam Style", "PSY", "Psy 6 (Six Rules), Part 1", "non-english", "2012 Korean-language global hit"),

    # --- ordinary, well-established songs, expected to resolve cleanly ---
    ("Billie Jean", "Michael Jackson", "Thriller", "ordinary", ""),
    ("Smells Like Teen Spirit", "Nirvana", "Nevermind", "ordinary", ""),
    ("Hotel California", "Eagles", "Hotel California", "ordinary", ""),
    ("Wonderwall", "Oasis", "(What's the Story) Morning Glory?", "ordinary", ""),
    ("Livin' on a Prayer", "Bon Jovi", "Slippery When Wet", "ordinary", ""),
    ("Toxic", "Britney Spears", "In the Zone", "ordinary", ""),
    ("Uptown Funk", "Mark Ronson", "Uptown Special", "ordinary", "credited with Bruno Mars as featured artist"),
    ("Sweet Child O' Mine", "Guns N' Roses", "Appetite for Destruction", "ordinary", ""),
    ("One More Time", "Daft Punk", "Discovery", "ordinary", ""),
    ("Shape of You", "Ed Sheeran", "÷", "ordinary", ""),
    ("Rolling in the Deep", "Adele", "21", "ordinary", ""),
    ("Africa", "Toto", "Toto IV", "ordinary", ""),
]

GROUND_TRUTH = {
    "Hallelujah": (1994,),
    "House of the Rising Sun": (1964,),
    "My Heart Will Go On": (1997,),
    "Take Five": (1959,),
    "La Vie en rose": (1947,),
    "Gangnam Style": (2012,),
    "Billie Jean": (1982,),
    "Smells Like Teen Spirit": (1991,),
    "Hotel California": (1976,),
    "Wonderwall": (1995,),
    "Livin' on a Prayer": (1986,),
    "Toxic": (2003,),
    "Uptown Funk": (2014,),
    "Sweet Child O' Mine": (1987,),
    "One More Time": (2000,),
    "Shape of You": (2017,),
    "Rolling in the Deep": (2010,),
    "Africa": (1982,),
}
