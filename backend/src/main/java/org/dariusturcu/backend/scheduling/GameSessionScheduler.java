package org.dariusturcu.backend.scheduling;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

// The thin scheduling layer for every round-level timer: the countdown after lock-in, the
// betting window, the active-player turn timeout, and the auto-abandon check. Each of
// those effects lives as a plain, directly-callable GameSessionService method with no
// dependency on real wall-clock waiting; this class only holds the TaskScheduler bean and
// converts a delay into an actual scheduled call. A unit test calls the effect method
// directly and asserts its outcome instantly; only the scheduling wiring itself needs a
// real (or drastically shortened) delay to prove out. See DECISIONS.md.
@Component
@RequiredArgsConstructor
public class GameSessionScheduler {

    private final TaskScheduler taskScheduler;

    public void scheduleAfter(Duration delay, Runnable effect) {
        taskScheduler.schedule(effect, Instant.now().plus(delay));
    }
}
