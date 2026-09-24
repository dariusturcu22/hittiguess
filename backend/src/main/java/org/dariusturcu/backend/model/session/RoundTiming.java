package org.dariusturcu.backend.model.session;

import java.time.Duration;

// Fixed lengths of a round's timed phases, shared by the service that schedules them and
// the mapper that publishes their deadlines so every client renders the same timers.
public final class RoundTiming {

    // Generous on purpose: the active player listens through YouTube's own ads before the
    // song, so this only stops an idle player from holding the round forever.
    public static final Duration PLACEMENT_WINDOW = Duration.ofMinutes(3);
    public static final Duration LOCK_IN_COUNTDOWN = Duration.ofSeconds(4);
    public static final Duration BETTING_WINDOW = Duration.ofSeconds(15);
    public static final Duration REVEAL_HOLD = Duration.ofSeconds(6);

    private RoundTiming() {
    }
}
