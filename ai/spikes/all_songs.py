"""Combines the four test batches into one 70-song set with a unified
ground truth, for the full MusicBrainz/Discogs/Wikidata/Wikipedia +
LLM-combination comparison. The first batch's 21 songs are filtered down
to the 19 scoreable ones (Solar Will and New Religion excluded, too
recently released for any ground truth to exist yet), matching
memory_accuracy_test.py's existing filtering. See spikes/README.md.
"""

from international_test_songs import GROUND_TRUTH as INTERNATIONAL_GROUND_TRUTH
from international_test_songs import INTERNATIONAL_TEST_SONGS
from mainstream_test_songs import GROUND_TRUTH as MAINSTREAM_GROUND_TRUTH
from mainstream_test_songs import MAINSTREAM_TEST_SONGS
from memory_accuracy_test import GROUND_TRUTH as FIRST_BATCH_GROUND_TRUTH
from ratio_test_songs import GROUND_TRUTH as RATIO_GROUND_TRUTH
from ratio_test_songs import RATIO_TEST_SONGS
from run_matrix import SONGS as FIRST_BATCH_ALL_SONGS

_FIRST_BATCH_SCOREABLE_SONGS = [song for song in FIRST_BATCH_ALL_SONGS if song[0] in FIRST_BATCH_GROUND_TRUTH]

# (title, artist, album, tier, note)
ALL_SONGS = _FIRST_BATCH_SCOREABLE_SONGS + RATIO_TEST_SONGS + INTERNATIONAL_TEST_SONGS + MAINSTREAM_TEST_SONGS

GROUND_TRUTH: dict[str, tuple[int, ...]] = {
    **FIRST_BATCH_GROUND_TRUTH,
    **RATIO_GROUND_TRUTH,
    **INTERNATIONAL_GROUND_TRUTH,
    **MAINSTREAM_GROUND_TRUTH,
}

assert len(ALL_SONGS) == len(GROUND_TRUTH), (
    f"{len(ALL_SONGS)} songs but {len(GROUND_TRUTH)} ground-truth entries, "
    "a title collision between batches would silently drop a song's truth"
)
