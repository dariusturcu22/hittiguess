package org.dariusturcu.backend.websocket;

// The kinds of session-side state change GameSessionService publishes as a
// SessionBroadcastEvent. SESSION_ENDED routes to the session's ended topic, every other
// type routes to its round topic, see SessionBroadcastListener.
public enum SessionEventType {
    ROUND_STARTED,
    PLACEMENT_PREVIEW,
    GUESS_LOCKED,
    BETTING_OPENED,
    BET_PLACED,
    BETTING_SKIP_VOTED,
    REVEAL_TRIGGERED,
    ROUND_SCORED,
    NEXT_ROUND,
    SESSION_ENDED
}
