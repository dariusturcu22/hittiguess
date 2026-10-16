package org.dariusturcu.backend.pipeline;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Exponential retry delay: the base delay after the first failed attempt, doubling with each
 * further attempt up to a cap. Jitter shortens each delay by up to a fixed fraction so jobs
 * that failed together do not all retry at the same moment.
 */
public class RetryBackoff {

    public static final Duration BASE_DELAY = Duration.ofSeconds(30);
    public static final Duration MAX_DELAY = Duration.ofMinutes(30);
    static final double JITTER_FRACTION = 0.2;
    private static final int FIRST_ATTEMPT = 1;
    private static final long DOUBLING_FACTOR = 2;

    // Supplies a value in [0, 1).
    private final DoubleSupplier randomFraction;

    public RetryBackoff() {
        this(() -> ThreadLocalRandom.current().nextDouble());
    }

    public RetryBackoff(DoubleSupplier randomFraction) {
        this.randomFraction = randomFraction;
    }

    /** The delay before the next try after the given number of failed attempts, counted from one. */
    public Duration delayAfter(int failedAttempts) {
        Duration cappedDelay = exponentialDelay(Math.max(failedAttempts, FIRST_ATTEMPT));
        long jitterMillis = Math.round(cappedDelay.toMillis() * JITTER_FRACTION * randomFraction.getAsDouble());
        return cappedDelay.minusMillis(jitterMillis);
    }

    private static Duration exponentialDelay(int failedAttempts) {
        Duration delay = BASE_DELAY;
        for (int attempt = FIRST_ATTEMPT; attempt < failedAttempts; attempt++) {
            delay = delay.multipliedBy(DOUBLING_FACTOR);
            if (delay.compareTo(MAX_DELAY) >= 0) {
                return MAX_DELAY;
            }
        }
        return delay;
    }
}
