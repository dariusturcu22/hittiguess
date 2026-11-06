package org.dariusturcu.backend.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterRegistryTest {

    private static final int MAX_REQUESTS_PER_WINDOW = 3;
    private static final Duration LONG_WINDOW = Duration.ofMinutes(10);
    private static final int MANY_KEYS = 500;

    @Test
    void requestsUpToAndIncludingTheLimitAreAllowed() {
        RateLimiterRegistry registry = new RateLimiterRegistry(MAX_REQUESTS_PER_WINDOW, LONG_WINDOW);
        String key = "same-key";

        for (int requestNumber = 0; requestNumber < MAX_REQUESTS_PER_WINDOW; requestNumber++) {
            assertThat(registry.tryConsume(key))
                    .as("request %d of %d should be allowed", requestNumber + 1, MAX_REQUESTS_PER_WINDOW)
                    .isTrue();
        }
    }

    @Test
    void theRequestOneOverTheLimitIsRejected() {
        RateLimiterRegistry registry = new RateLimiterRegistry(MAX_REQUESTS_PER_WINDOW, LONG_WINDOW);
        String key = "same-key";

        for (int requestNumber = 0; requestNumber < MAX_REQUESTS_PER_WINDOW; requestNumber++) {
            registry.tryConsume(key);
        }

        assertThat(registry.tryConsume(key)).isFalse();
    }

    @Test
    void differentKeysAreRateLimitedIndependently() {
        RateLimiterRegistry registry = new RateLimiterRegistry(MAX_REQUESTS_PER_WINDOW, LONG_WINDOW);

        for (int requestNumber = 0; requestNumber < MAX_REQUESTS_PER_WINDOW; requestNumber++) {
            registry.tryConsume("key-one");
        }
        assertThat(registry.tryConsume("key-one")).isFalse();

        assertThat(registry.tryConsume("key-two")).isTrue();
    }

    @Test
    void idleKeysAreDroppedAfterAFullWindowAndStartWithAFullBucket() {
        AtomicLong nanoClock = new AtomicLong();
        RateLimiterRegistry registry = new RateLimiterRegistry(MAX_REQUESTS_PER_WINDOW, LONG_WINDOW, nanoClock::get);
        for (int keyNumber = 0; keyNumber < MANY_KEYS; keyNumber++) {
            registry.tryConsume("key-" + keyNumber);
        }
        assertThat(registry.trackedKeyCount()).isEqualTo(MANY_KEYS);

        nanoClock.addAndGet(LONG_WINDOW.toNanos());
        registry.tryConsume("fresh-key");

        assertThat(registry.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void keysUsedWithinTheWindowSurviveASweep() {
        AtomicLong nanoClock = new AtomicLong();
        RateLimiterRegistry registry = new RateLimiterRegistry(MAX_REQUESTS_PER_WINDOW, LONG_WINDOW, nanoClock::get);
        registry.tryConsume("early-key");
        nanoClock.addAndGet(LONG_WINDOW.toNanos() / 2);
        registry.tryConsume("active-key");

        nanoClock.addAndGet(LONG_WINDOW.toNanos() / 2);
        registry.tryConsume("active-key");

        assertThat(registry.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void clearForgetsEveryKey() {
        RateLimiterRegistry registry = new RateLimiterRegistry(MAX_REQUESTS_PER_WINDOW, LONG_WINDOW);
        for (int requestNumber = 0; requestNumber < MAX_REQUESTS_PER_WINDOW; requestNumber++) {
            registry.tryConsume("same-key");
        }

        registry.clear();

        assertThat(registry.tryConsume("same-key")).isTrue();
    }
}
