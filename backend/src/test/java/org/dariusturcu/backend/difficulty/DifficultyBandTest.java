package org.dariusturcu.backend.difficulty;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DifficultyBandTest {

    private final DifficultyBand difficultyBand = new DifficultyBand();

    private static final double JUST_BELOW = 1.0e-6;

    @Test
    void theEasiestScoreBandsAsEasy() {
        assertThat(difficultyBand.tierForScore(SongDifficultyScorer.DIFFICULTY_SCORE_EASIEST))
                .isEqualTo(DifficultyTier.EASY);
    }

    @Test
    void aScoreJustBelowTheEasyBoundBandsAsEasy() {
        assertThat(difficultyBand.tierForScore(DifficultyBand.EASY_BAND_UPPER_BOUND - JUST_BELOW))
                .isEqualTo(DifficultyTier.EASY);
    }

    @Test
    void theEasyUpperBoundItselfBandsAsMedium() {
        assertThat(difficultyBand.tierForScore(DifficultyBand.EASY_BAND_UPPER_BOUND))
                .isEqualTo(DifficultyTier.MEDIUM);
    }

    @Test
    void aScoreJustBelowTheMediumBoundBandsAsMedium() {
        assertThat(difficultyBand.tierForScore(DifficultyBand.MEDIUM_BAND_UPPER_BOUND - JUST_BELOW))
                .isEqualTo(DifficultyTier.MEDIUM);
    }

    @Test
    void theMediumUpperBoundItselfBandsAsHard() {
        assertThat(difficultyBand.tierForScore(DifficultyBand.MEDIUM_BAND_UPPER_BOUND))
                .isEqualTo(DifficultyTier.HARD);
    }

    @Test
    void theHardestScoreBandsAsHard() {
        assertThat(difficultyBand.tierForScore(SongDifficultyScorer.DIFFICULTY_SCORE_HARDEST))
                .isEqualTo(DifficultyTier.HARD);
    }
}
