package org.dariusturcu.backend.ratelimit;

import io.github.bucket4j.Bucket;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Fixed-capacity, greedily-refilled token bucket per key. A bucket idle for a full window
 * has refilled completely, so it is dropped by a periodic sweep and recreated on the next
 * request, which keeps memory bounded by the keys active within one window. Fine for a
 * single-instance deployment; a distributed deployment would need buckets backed by shared
 * storage instead.
 */
public class RateLimiterRegistry {

    private static final class TrackedBucket {
        private final Bucket bucket;
        private volatile long lastAccessNanos;

        private TrackedBucket(Bucket bucket, long createdAtNanos) {
            this.bucket = bucket;
            this.lastAccessNanos = createdAtNanos;
        }
    }

    private final ConcurrentHashMap<String, TrackedBucket> bucketsByKey = new ConcurrentHashMap<>();
    private final int maxRequestsPerWindow;
    private final Duration window;
    private final long idleExpiryNanos;
    private final LongSupplier nanoClock;
    private final AtomicLong lastSweepNanos;

    public RateLimiterRegistry(int maxRequestsPerWindow, Duration window) {
        this(maxRequestsPerWindow, window, System::nanoTime);
    }

    RateLimiterRegistry(int maxRequestsPerWindow, Duration window, LongSupplier nanoClock) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.window = window;
        this.idleExpiryNanos = window.toNanos();
        this.nanoClock = nanoClock;
        this.lastSweepNanos = new AtomicLong(nanoClock.getAsLong());
    }

    public boolean tryConsume(String key) {
        long nowNanos = nanoClock.getAsLong();
        sweepIfDue(nowNanos);
        TrackedBucket tracked = bucketsByKey.computeIfAbsent(key, ignoredKey -> new TrackedBucket(newBucket(), nowNanos));
        tracked.lastAccessNanos = nowNanos;
        return tracked.bucket.tryConsume(1);
    }

    int trackedKeyCount() {
        return bucketsByKey.size();
    }

    void clear() {
        bucketsByKey.clear();
    }

    private void sweepIfDue(long nowNanos) {
        long previousSweepNanos = lastSweepNanos.get();
        if (nowNanos - previousSweepNanos < idleExpiryNanos
                || !lastSweepNanos.compareAndSet(previousSweepNanos, nowNanos)) {
            return;
        }
        bucketsByKey.values().removeIf(tracked -> nowNanos - tracked.lastAccessNanos >= idleExpiryNanos);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(maxRequestsPerWindow).refillGreedy(maxRequestsPerWindow, window))
                .build();
    }
}
