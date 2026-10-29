"""The year decision replayed over recorded source answers for songs with a known release
year (ai/benchmark). A change to the lock rule that locks a wrong year fails here; a song
the rule leaves unresolved goes to reconciliation and review, which is allowed but counted."""
import pytest

from benchmark.evaluate import evidence, load_captures, production_rule
from benchmark.capture import DEFAULT_SONGS_FILE

MAXIMUM_UNRESOLVED_SONGS = 1

CAPTURES = load_captures(DEFAULT_SONGS_FILE)


def test_every_benchmark_song_has_recorded_answers():
    assert len(CAPTURES) >= 65


@pytest.mark.parametrize("capture", CAPTURES, ids=lambda capture: f"{capture['title']} ({capture['main_artists'][0]})")
def test_the_lock_never_settles_on_a_wrong_year(capture):
    decided = production_rule(capture, evidence(capture))

    assert decided in (None, capture["expected_year"])


def test_few_benchmark_songs_are_left_to_reconciliation():
    unresolved = [capture["title"] for capture in CAPTURES if production_rule(capture, evidence(capture)) is None]

    assert len(unresolved) <= MAXIMUM_UNRESOLVED_SONGS, unresolved
