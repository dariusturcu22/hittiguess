package org.dariusturcu.backend.util;

import java.time.Duration;

/**
 * Spaces calls to one rate-limited endpoint across every thread using it: each caller
 * reserves the next free slot under a lock, then sleeps until that slot outside it, so
 * concurrent callers queue at the endpoint's pace instead of bursting past its limit.
 */
public class RequestPacer {

    private final long intervalNanos;
    private long nextSlotNanos;

    public RequestPacer(Duration interval) {
        this.intervalNanos = interval.toNanos();
        this.nextSlotNanos = System.nanoTime();
    }

    public void awaitSlot() throws InterruptedException {
        long reservedSlotNanos;
        synchronized (this) {
            reservedSlotNanos = Math.max(System.nanoTime(), nextSlotNanos);
            nextSlotNanos = reservedSlotNanos + intervalNanos;
        }
        long waitNanos = reservedSlotNanos - System.nanoTime();
        if (waitNanos > 0) {
            Thread.sleep(Duration.ofNanos(waitNanos));
        }
    }
}
