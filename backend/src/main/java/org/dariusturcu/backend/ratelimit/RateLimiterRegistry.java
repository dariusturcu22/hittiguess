package org.dariusturcu.backend.ratelimit;

import io.github.bucket4j.Bucket;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-capacity, greedily-refilled token bucket per key, keeping one bucket alive
 * for the lifetime of the registry. Fine for a single-instance deployment; a
 * distributed deployment would need buckets backed by shared storage instead.
 */
public class RateLimiterRegistry {
    private final ConcurrentHashMap<String, Bucket> bucketsByKey = new ConcurrentHashMap<>();
    private final int maxRequestsPerWindow;
    private final Duration window;

    public RateLimiterRegistry(int maxRequestsPerWindow, Duration window) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.window = window;
    }

    public boolean tryConsume(String key) {
        Bucket bucket = bucketsByKey.computeIfAbsent(key, ignoredKey -> newBucket());
        return bucket.tryConsume(1);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(maxRequestsPerWindow).refillGreedy(maxRequestsPerWindow, window))
                .build();
    }
}
