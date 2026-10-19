package org.dariusturcu.backend.model.session;

// A round's lifecycle, driven by GameSessionService and, for the countdown and betting
// steps, by GameSessionScheduler's timers.
public enum RoundStatus {
    AWAITING_PLACEMENT,
    COUNTDOWN,
    BETTING,
    REVEALED,
    SCORED
}
