package org.dariusturcu.backend.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

class RequestPacerTest {

    private static final Duration INTERVAL = Duration.ofMillis(40);
    private static final int CONCURRENT_CALLER_COUNT = 6;
    // Absorbs scheduler jitter between a sleep ending and the timestamp being taken.
    private static final double TIMING_TOLERANCE_FRACTION = 0.8;

    @Test
    void concurrentCallersAreSpacedByTheIntervalInsteadOfBursting() throws InterruptedException {
        RequestPacer pacer = new RequestPacer(INTERVAL);
        List<Long> releaseTimes = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Thread> callers = new ArrayList<>();
        for (int callerIndex = 0; callerIndex < CONCURRENT_CALLER_COUNT; callerIndex++) {
            Thread caller = new Thread(() -> {
                try {
                    startTogether.await();
                    pacer.awaitSlot();
                    releaseTimes.add(System.nanoTime());
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                }
            });
            caller.start();
            callers.add(caller);
        }

        startTogether.countDown();
        for (Thread caller : callers) {
            caller.join();
        }

        List<Long> ordered = releaseTimes.stream().sorted().toList();
        assertThat(ordered).hasSize(CONCURRENT_CALLER_COUNT);
        for (int index = 1; index < ordered.size(); index++) {
            long gapNanos = ordered.get(index) - ordered.get(index - 1);
            assertThat(gapNanos).isGreaterThanOrEqualTo((long) (INTERVAL.toNanos() * TIMING_TOLERANCE_FRACTION));
        }
    }
}
