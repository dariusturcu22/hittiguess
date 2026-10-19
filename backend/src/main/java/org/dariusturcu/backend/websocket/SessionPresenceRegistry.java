package org.dariusturcu.backend.websocket;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// The session-side counterpart to GroupPresenceRegistry: an in-memory map from a STOMP
// session id to the game session and player user its socket subscribed for. A client
// holds several sockets at once, so a player only counts as disconnected once none of
// their sockets for that game session remains registered.
@Component
public class SessionPresenceRegistry {

    public record PlayerSocket(Long sessionId, Long userId) {
    }

    private final Map<String, PlayerSocket> playerSocketByStompSessionId = new ConcurrentHashMap<>();

    public void register(String stompSessionId, Long sessionId, Long userId) {
        playerSocketByStompSessionId.put(stompSessionId, new PlayerSocket(sessionId, userId));
    }

    public Optional<PlayerSocket> remove(String stompSessionId) {
        return Optional.ofNullable(playerSocketByStompSessionId.remove(stompSessionId));
    }

    public boolean hasOpenSocket(Long sessionId, Long userId) {
        return playerSocketByStompSessionId.containsValue(new PlayerSocket(sessionId, userId));
    }
}
