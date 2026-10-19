package org.dariusturcu.backend.pipeline;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryBackoffTest {

    private static final double NO_JITTER = 0.0;
    private static final double HALF_JITTER = 0.5;
    private static final double ALMOST_FULL_JITTER = 0.999_999;
    private static final int ATTEMPT_PAST_THE_CAP = 20;

    private final RetryBackoff withoutJitter = new RetryBackoff(() -> NO_JITTER);

    @Test
    void theFirstRetryWaitsTheBaseDelay() {
        assertThat(withoutJitter.delayAfter(1)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void eachFurtherAttemptDoublesTheDelay() {
        assertThat(withoutJitter.delayAfter(2)).isEqualTo(Duration.ofMinutes(1));
        assertThat(withoutJitter.delayAfter(3)).isEqualTo(Duration.ofMinutes(2));
        assertThat(withoutJitter.delayAfter(4)).isEqualTo(Duration.ofMinutes(4));
        assertThat(withoutJitter.delayAfter(6)).isEqualTo(Duration.ofMinutes(16));
    }

    @Test
    void theDelayStopsGrowingAtTheCap() {
        assertThat(withoutJitter.delayAfter(7)).isEqualTo(RetryBackoff.MAX_DELAY);
        assertThat(withoutJitter.delayAfter(ATTEMPT_PAST_THE_CAP)).isEqualTo(RetryBackoff.MAX_DELAY);
        assertThat(withoutJitter.delayAfter(Integer.MAX_VALUE)).isEqualTo(RetryBackoff.MAX_DELAY);
    }

    @Test
    void anAttemptCountBelowOneIsTreatedAsTheFirst() {
        assertThat(withoutJitter.delayAfter(0)).isEqualTo(RetryBackoff.BASE_DELAY);
    }

    @Test
    void jitterShortensTheDelayByAtMostItsFraction() {
        RetryBackoff halfJitter = new RetryBackoff(() -> HALF_JITTER);
        RetryBackoff almostFullJitter = new RetryBackoff(() -> ALMOST_FULL_JITTER);

        assertThat(halfJitter.delayAfter(1)).isEqualTo(Duration.ofSeconds(27));
        assertThat(halfJitter.delayAfter(ATTEMPT_PAST_THE_CAP)).isEqualTo(Duration.ofMinutes(27));
        assertThat(almostFullJitter.delayAfter(1)).isEqualTo(Duration.ofSeconds(24));
    }
}
