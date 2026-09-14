package org.dariusturcu.backend.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterRegistryTest {

    private static final int MAX_REQUESTS_PER_WINDOW = 3;
    private static final Duration LONG_WINDOW = Duration.ofMinutes(10);

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
}
