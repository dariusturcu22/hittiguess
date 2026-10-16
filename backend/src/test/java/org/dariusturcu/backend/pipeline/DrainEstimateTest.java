package org.dariusturcu.backend.pipeline;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DrainEstimateTest {
    private static final long REMAINING = 6;
    private static final long PACED_REMAINING = 10;
    private static final int YOUTUBE_LIMIT = StageConcurrency.DEFAULT_YOUTUBE_METADATA_LIMIT;
    private static final int ONE_SLOT = 1;
    private static final long TWENTY_SECONDS_MS = Duration.ofSeconds(20).toMillis();
    private static final long ONE_MINUTE_MS = Duration.ofMinutes(1).toMillis();
    private static final long FAST_JOB_MS = 100;
    private static final int PACE_INTERVAL_MS = 1500;
    private static final long NONE_FINISHED = 0;
    private static final long ONE_FINISHED = 1;
    private static final long TWO_FINISHED = 2;
    private static final long THIRTY_FINISHED = 30;
    private static final long FIVE_FINISHED = 5;
    private static final double HALF_A_JOB_PER_MINUTE = 0.5;
    private static final Duration NO_COOLDOWN = Duration.ZERO;
    private static final Duration COOLDOWN = Duration.ofMillis(90_500);
    private static final long COOLDOWN_CEILING_SECONDS = 91;
    // Four slots at twenty seconds per job finish twelve jobs a minute, so six take thirty seconds.
    private static final long SIX_JOBS_AT_TWELVE_PER_MINUTE_SECONDS = 30;
    // Thirty jobs in ten minutes is three a minute, so six take two minutes.
    private static final long SIX_JOBS_AT_THREE_PER_MINUTE_SECONDS = 120;
    // A 1.5-second pace allows forty jobs a minute, so ten take fifteen seconds.
    private static final long TEN_JOBS_AT_FORTY_PER_MINUTE_SECONDS = 15;
    private static final long SHORT_ESTIMATE = 40;
    private static final long LONG_ESTIMATE = 300;

    private static DrainEstimate.Inputs inputs(long remaining, int limit, Long averageMs, long finished,
                                               Integer paceMs, boolean paused, Duration cooldown) {
        return new DrainEstimate.Inputs(remaining, limit, averageMs, finished, paceMs, paused, cooldown);
    }

    @Test
    void noFinishedJobAndNoDurationLeavesTheEstimateUnknown() {
        assertThat(DrainEstimate.seconds(inputs(REMAINING, YOUTUBE_LIMIT, null, NONE_FINISHED, null, false, NO_COOLDOWN)))
                .isNull();
        assertThat(DrainEstimate.seconds(inputs(REMAINING, YOUTUBE_LIMIT, null, ONE_FINISHED, null, false, NO_COOLDOWN)))
                .isNull();
    }

    @Test
    void anEmptyStageHasNothingLeftEvenWhilePaused() {
        assertThat(DrainEstimate.seconds(inputs(0, YOUTUBE_LIMIT, null, NONE_FINISHED, null, true, COOLDOWN))).isZero();
    }

    @Test
    void aSingleFinishedJobUsesTheConcurrencyOverItsDuration() {
        assertThat(DrainEstimate.seconds(inputs(REMAINING, YOUTUBE_LIMIT, TWENTY_SECONDS_MS, ONE_FINISHED, null, false,
                NO_COOLDOWN))).isEqualTo(SIX_JOBS_AT_TWELVE_PER_MINUTE_SECONDS);
    }

    @Test
    void theFasterOfMeasuredAndModelledThroughputWins() {
        assertThat(DrainEstimate.seconds(inputs(REMAINING, ONE_SLOT, ONE_MINUTE_MS, THIRTY_FINISHED, null, false,
                NO_COOLDOWN))).isEqualTo(SIX_JOBS_AT_THREE_PER_MINUTE_SECONDS);
        assertThat(DrainEstimate.seconds(inputs(REMAINING, YOUTUBE_LIMIT, TWENTY_SECONDS_MS, TWO_FINISHED, null, false,
                NO_COOLDOWN))).isEqualTo(SIX_JOBS_AT_TWELVE_PER_MINUTE_SECONDS);
    }

    @Test
    void aPacedSourceNeverDrainsFasterThanItsPace() {
        assertThat(DrainEstimate.seconds(inputs(PACED_REMAINING, StageConcurrency.DEFAULT_SOURCE_LIMIT, FAST_JOB_MS,
                THIRTY_FINISHED, PACE_INTERVAL_MS, false, NO_COOLDOWN))).isEqualTo(TEN_JOBS_AT_FORTY_PER_MINUTE_SECONDS);
    }

    @Test
    void aPausedStageIsUnknownAndACooldownIsAddedInWholeSeconds() {
        assertThat(DrainEstimate.seconds(inputs(REMAINING, YOUTUBE_LIMIT, TWENTY_SECONDS_MS, THIRTY_FINISHED, null, true,
                NO_COOLDOWN))).isNull();
        assertThat(DrainEstimate.seconds(inputs(REMAINING, YOUTUBE_LIMIT, TWENTY_SECONDS_MS, ONE_FINISHED, null, false,
                COOLDOWN))).isEqualTo(SIX_JOBS_AT_TWELVE_PER_MINUTE_SECONDS + COOLDOWN_CEILING_SECONDS);
    }

    @Test
    void measuredThroughputIsJobsPerMinuteOverTheWindow() {
        assertThat(DrainEstimate.measuredJobsPerMinute(FIVE_FINISHED)).isEqualTo(HALF_A_JOB_PER_MINUTE);
        assertThat(DrainEstimate.measuredJobsPerMinute(NONE_FINISHED)).isZero();
    }

    @Test
    void theOverallEstimateIsTheSlowestStageAndUnknownWhenABusyStageIsUnknown() {
        assertThat(DrainEstimate.overall(List.of(new DrainEstimate.StageEstimate(REMAINING, SHORT_ESTIMATE),
                new DrainEstimate.StageEstimate(REMAINING, LONG_ESTIMATE), new DrainEstimate.StageEstimate(0, null))))
                .isEqualTo(LONG_ESTIMATE);
        assertThat(DrainEstimate.overall(List.of(new DrainEstimate.StageEstimate(REMAINING, SHORT_ESTIMATE),
                new DrainEstimate.StageEstimate(REMAINING, null)))).isNull();
        assertThat(DrainEstimate.overall(List.of(new DrainEstimate.StageEstimate(0, 0L)))).isZero();
    }
}
