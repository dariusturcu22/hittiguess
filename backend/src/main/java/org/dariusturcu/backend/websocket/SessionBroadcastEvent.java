package org.dariusturcu.backend.websocket;

// Published by GameSessionService whenever round or session state changes that other
// players need to see live, the session-side counterpart to GroupBroadcastEvent. payload
// is a RoundDTO for every round-lifecycle type, or a SessionResultsDTO (possibly null, on
// an abandonment) for SESSION_ENDED.
public record SessionBroadcastEvent(SessionEventType type, Long sessionId, Object payload) {
}
