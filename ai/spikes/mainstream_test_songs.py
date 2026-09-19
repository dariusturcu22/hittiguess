"""Fourth batch, 21 more mainstream/well-known songs across eras and
regions, pulling the combined set from 49 to 70. Balances the existing
three batches' adversarial/niche lean with more ordinary hits, the same
reasoning as the second batch's "ordinary" tier: a ratio or accuracy
number derived only from hard cases skews toward whatever handles chaos
best, not what handles a normal playlist best. Ground truth researched
via web search against each song's own Wikipedia article.
One deliberate single-vs-album trap included ("Smooth Criminal", single
1988, parent album "Bad" 1987, earlier). See spikes/README.md.
"""

# (title, artist, album, tier, note)
MAINSTREAM_TEST_SONGS = [
    ("Despacito", "Luis Fonsi", None, "ordinary", "2017-01-13"),
    ("Dance Monkey", "Tones and I", None, "ordinary", "2019-05-10"),
    ("Blinding Lights", "The Weeknd", "After Hours", "ordinary", "2019-11-29"),
    ("How You Like That", "BLACKPINK", "The Album", "ordinary", "2020-06-26"),
    ("Dynamite", "BTS", None, "ordinary", "2020-08-21"),
    ("Titi Me Pregunto", "Bad Bunny", "Un Verano Sin Ti", "ordinary", "album released 2022-05-06, single 2022-06-01, earlier of the two is the album"),
    ("Lose Yourself", "Eminem", None, "ordinary", "2002-10-28, from the 8 Mile soundtrack"),
    ("Rap God", "Eminem", "The Marshall Mathers LP 2", "ordinary", "2013-10-15"),
    ("Like a Rolling Stone", "Bob Dylan", None, "ordinary", "1965-07-20"),
    ("Imagine", "John Lennon", "Imagine", "ordinary", "1971-10-11"),
    ("Get Lucky", "Daft Punk", "Random Access Memories", "ordinary", "2013-04-19"),
    ("Waka Waka (This Time for Africa)", "Shakira", None, "ordinary", "2010-05-07"),
    ("Smooth Criminal", "Michael Jackson", "Bad", "reissue", "single released 1988-10-13, but the parent album Bad came out 1987, earlier, deliberate single-vs-album trap"),
    ("Enter Sandman", "Metallica", "Metallica", "ordinary", "1991-07-29"),
    ("Last Last", "Burna Boy", None, "afrobeats", "2022-05-13"),
    ("Calm Down", "Rema", None, "afrobeats", "2022-02-11"),
    ("Levitating", "Dua Lipa", "Future Nostalgia", "ordinary", "single 2020-10-01, album 2020-03-27, both 2020"),
    ("As It Was", "Harry Styles", None, "ordinary", "2022-04-01"),
    ("Bad Romance", "Lady Gaga", None, "ordinary", "2009-10-19"),
    ("Faded", "Alan Walker", None, "ordinary", "2015-12-03"),
    ("Drivers License", "Olivia Rodrigo", None, "ordinary", "2021-01-08"),
]

GROUND_TRUTH = {
    "Despacito": (2017,),
    "Dance Monkey": (2019,),
    "Blinding Lights": (2019,),
    "How You Like That": (2020,),
    "Dynamite": (2020,),
    "Titi Me Pregunto": (2022,),
    "Lose Yourself": (2002,),
    "Rap God": (2013,),
    "Like a Rolling Stone": (1965,),
    "Imagine": (1971,),
    "Get Lucky": (2013,),
    "Waka Waka (This Time for Africa)": (2010,),
    "Smooth Criminal": (1987,),
    "Enter Sandman": (1991,),
    "Last Last": (2022,),
    "Calm Down": (2022,),
    "Levitating": (2020,),
    "As It Was": (2022,),
    "Bad Romance": (2009,),
    "Faded": (2015,),
    "Drivers License": (2021,),
}
