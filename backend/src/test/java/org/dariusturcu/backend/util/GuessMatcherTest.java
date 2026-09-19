package org.dariusturcu.backend.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Exercises the normalization and edit-distance-1 rule from DECISIONS.md's
// "Typo-tolerance threshold for in-round title/artist guesses" entry, including the
// worked examples that entry calibrated against.
class GuessMatcherTest {

    @Test
    void normalizeLowercasesStripsDiacriticsPunctuationAndCollapsesWhitespace() {
        assertThat(GuessMatcher.normalize("  Bohémian   Rhapsody!!  ")).isEqualTo("bohemian rhapsody");
        assertThat(GuessMatcher.normalize("Guns N' Roses")).isEqualTo("guns n roses");
    }

    @Test
    void aSingleCharacterTypoIsWithinBudgetAndCountsAsCorrect() {
        // Deletion: one missing letter.
        assertThat(GuessMatcher.matches("Qeen", "Queen")).isTrue();
        // Substitution: one swapped letter.
        assertThat(GuessMatcher.matches("Bohemian Rhapsofy", "Bohemian Rhapsody")).isTrue();
    }

    @Test
    void twoEditsAwayFallsOutsideTheBudget() {
        // Two substitutions ("s"->"f" and "o"->"a"): distance 2, past the flat budget of 1.
        assertThat(GuessMatcher.matches("Bohemian Rhapfady", "Bohemian Rhapsody")).isFalse();
    }

    @Test
    void anAdjacentTranspositionCountsAsOneEditNotTwo() {
        // "Rhapsody" -> "Rhapsdoy": the trailing "od" and "y" swapped to "oy" and "d",
        // i.e. one adjacent transposition. Plain Levenshtein would need two edits
        // (a deletion plus an insertion, or two substitutions); Damerau-Levenshtein
        // collapses it to one, landing inside the budget.
        assertThat(GuessMatcher.matches("Bohemian Rhapsdoy", "Bohemian Rhapsody")).isTrue();
    }

    @Test
    void aMissingWordFailsDespiteNormalization() {
        // DECISIONS.md's worked example: "Beatles" for "The Beatles" lands well past an
        // edit distance of 1 once compared as one normalized string.
        assertThat(GuessMatcher.matches("Beatles", "The Beatles")).isFalse();
    }

    @Test
    void aReorderedPairOfWordsFails() {
        assertThat(GuessMatcher.matches("Rhapsody Bohemian", "Bohemian Rhapsody")).isFalse();
    }

    @Test
    void anAddedWordFails() {
        assertThat(GuessMatcher.matches("Bohemian Rhapsody Song", "Bohemian Rhapsody")).isFalse();
    }

    @Test
    void aShortUnrelatedRealWordSwappedInFailsTheSameAsALongTypo() {
        assertThat(GuessMatcher.matches("Guns App Roses", "Guns N' Roses")).isFalse();
        assertThat(GuessMatcher.matches("Guns and Roses", "Guns N' Roses")).isFalse();
    }

    @Test
    void blankOrEmptyGuessesNeverMatch() {
        assertThat(GuessMatcher.matches("", "Queen")).isFalse();
        assertThat(GuessMatcher.matches("   ", "Queen")).isFalse();
        assertThat(GuessMatcher.matches(null, "Queen")).isFalse();
    }

    @Test
    void matchesAnyArtistAcceptsASingleCorrectNameOnAMultiArtistSong() {
        List<String> artistNames = List.of("Queen", "David Bowie");

        assertThat(GuessMatcher.matchesAnyArtist("David Bowie", artistNames)).isTrue();
        assertThat(GuessMatcher.matchesAnyArtist("Queeen", artistNames)).isTrue();
        assertThat(GuessMatcher.matchesAnyArtist("Elton John", artistNames)).isFalse();
    }
}
