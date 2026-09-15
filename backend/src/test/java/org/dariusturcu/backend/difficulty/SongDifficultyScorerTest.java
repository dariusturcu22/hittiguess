package org.dariusturcu.backend.difficulty;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SongDifficultyScorerTest {

    private final SongDifficultyScorer songDifficultyScorer = new SongDifficultyScorer();

    private static final Long SONG_ID = 1L;
    private static final long FULLY_TRUSTED_ROUND_COUNT = SongDifficultyScorer.PLAY_HISTORY_FULL_TRUST_ROUND_COUNT;
    private static final int WIDELY_KNOWN_SITELINKS = SongDifficultyScorer.SITELINKS_WIDELY_KNOWN_COUNT;
    private static final double SCORE_TOLERANCE = 1.0e-9;

    @Test
    void aSongPlacedCorrectlyEveryTimeScoresEasiestWhenFullyTrusted() {
        SongPlacementStats alwaysCorrect =
                new SongPlacementStats(SONG_ID, FULLY_TRUSTED_ROUND_COUNT, FULLY_TRUSTED_ROUND_COUNT);
        SongDifficultySignals signals = new SongDifficultySignals(SONG_ID, alwaysCorrect, Optional.empty());

        double score = songDifficultyScorer.score(signals);

        assertThat(score).isCloseTo(SongDifficultyScorer.DIFFICULTY_SCORE_EASIEST, within(SCORE_TOLERANCE));
    }

    @Test
    void aSongPlacedWrongEveryTimeScoresHardestWhenFullyTrusted() {
        long noCorrectPlacements = 0L;
        SongPlacementStats alwaysWrong =
                new SongPlacementStats(SONG_ID, FULLY_TRUSTED_ROUND_COUNT, noCorrectPlacements);
        SongDifficultySignals signals = new SongDifficultySignals(SONG_ID, alwaysWrong, Optional.empty());

        double score = songDifficultyScorer.score(signals);

        assertThat(score).isCloseTo(SongDifficultyScorer.DIFFICULTY_SCORE_HARDEST, within(SCORE_TOLERANCE));
    }

    @Test
    void aSongWithNoSignalAtAllScoresNeutral() {
        SongDifficultySignals noSignals = new SongDifficultySignals(SONG_ID, null, Optional.empty());

        double score = songDifficultyScorer.score(noSignals);

        assertThat(score).isCloseTo(SongDifficultyScorer.NEUTRAL_DIFFICULTY_SCORE, within(SCORE_TOLERANCE));
    }

    @Test
    void aSongWithNoPlayHistoryButWidelyKnownFallsBackToTheEasySitelinksSignal() {
        SongDifficultySignals popularNeverPlayed =
                new SongDifficultySignals(SONG_ID, null, Optional.of(WIDELY_KNOWN_SITELINKS));

        double score = songDifficultyScorer.score(popularNeverPlayed);

        assertThat(score).isCloseTo(SongDifficultyScorer.DIFFICULTY_SCORE_EASIEST, within(SCORE_TOLERANCE));
    }

    @Test
    void aSongWithNoPlayHistoryAndNoSitelinksFallsBackToNeutral() {
        SongPlacementStats zeroRounds = new SongPlacementStats(SONG_ID, 0L, 0L);
        SongDifficultySignals coldStart = new SongDifficultySignals(SONG_ID, zeroRounds, Optional.empty());

        double score = songDifficultyScorer.score(coldStart);

        assertThat(score).isCloseTo(SongDifficultyScorer.NEUTRAL_DIFFICULTY_SCORE, within(SCORE_TOLERANCE));
    }

    @Test
    void thinPlayHistoryIsBlendedWithTheSitelinksProxyRatherThanTrustedAlone() {
        long singleRound = 1L;
        long correctPlacement = 1L;
        SongPlacementStats oneCorrectRound = new SongPlacementStats(SONG_ID, singleRound, correctPlacement);
        int nicheSitelinks = SongDifficultyScorer.SITELINKS_MINIMUM_COUNT;
        SongDifficultySignals thinHistory =
                new SongDifficultySignals(SONG_ID, oneCorrectRound, Optional.of(nicheSitelinks));

        double blended = songDifficultyScorer.score(thinHistory);

        SongDifficultySignals fullyTrustedSameOutcome = new SongDifficultySignals(
                SONG_ID,
                new SongPlacementStats(SONG_ID, FULLY_TRUSTED_ROUND_COUNT, FULLY_TRUSTED_ROUND_COUNT),
                Optional.of(nicheSitelinks));
        double fullyTrusted = songDifficultyScorer.score(fullyTrustedSameOutcome);

        assertThat(blended).isGreaterThan(fullyTrusted);
    }

    @Test
    void aWidelyKnownSongPlacedCorrectlyEveryTimeStaysInTheEasiestBand() {
        SongPlacementStats alwaysCorrect =
                new SongPlacementStats(SONG_ID, FULLY_TRUSTED_ROUND_COUNT, FULLY_TRUSTED_ROUND_COUNT);
        SongDifficultySignals easyEverywhere =
                new SongDifficultySignals(SONG_ID, alwaysCorrect, Optional.of(WIDELY_KNOWN_SITELINKS));

        double score = songDifficultyScorer.score(easyEverywhere);

        assertThat(score).isLessThan(DifficultyBand.EASY_BAND_UPPER_BOUND);
    }
}
