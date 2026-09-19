package org.dariusturcu.backend.websocket;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// The session-side counterpart to GroupPresenceRegistry: an in-memory map from a STOMP
// session id to the game session its client subscribed to. Populated when a client
// subscribes to its session's round topic, read on disconnect to resolve which
// in-session Player to flip isConnected false on.
@Component
public class SessionPresenceRegistry {

    private final Map<String, Long> sessionIdByStompSessionId = new ConcurrentHashMap<>();

    public void register(String stompSessionId, Long sessionId) {
        sessionIdByStompSessionId.put(stompSessionId, sessionId);
    }

    public Optional<Long> sessionIdFor(String stompSessionId) {
        return Optional.ofNullable(sessionIdByStompSessionId.get(stompSessionId));
    }

    public void remove(String stompSessionId) {
        sessionIdByStompSessionId.remove(stompSessionId);
    }
}
