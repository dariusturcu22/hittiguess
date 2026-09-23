package org.dariusturcu.backend.model.session;

import java.time.Duration;

// Fixed lengths of a round's timed phases, shared by the service that schedules them and
// the mapper that publishes their deadlines so every client renders the same timers.
public final class RoundTiming {

    public static final Duration LOCK_IN_COUNTDOWN = Duration.ofSeconds(4);
    public static final Duration BETTING_WINDOW = Duration.ofSeconds(15);
    public static final Duration REVEAL_HOLD = Duration.ofSeconds(6);

    private RoundTiming() {
    }
}
