package org.dariusturcu.backend.websocket;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// In-memory map from a STOMP session id to the group its client subscribed to. Populated
// when a session subscribes to its group's membership topic, read when that session
// disconnects so the disconnect handler knows which group's member to flip isConnected
// false on. Deliberately in-memory and per-instance: group membership state itself
// already lives in Postgres, this registry only tracks which live socket session belongs
// to which group, and is rebuilt automatically as clients resubscribe after a restart.
@Component
public class GroupPresenceRegistry {

    private final Map<String, Long> groupIdBySessionId = new ConcurrentHashMap<>();

    public void register(String sessionId, Long groupId) {
        groupIdBySessionId.put(sessionId, groupId);
    }

    public Optional<Long> groupIdFor(String sessionId) {
        return Optional.ofNullable(groupIdBySessionId.get(sessionId));
    }

    public void remove(String sessionId) {
        groupIdBySessionId.remove(sessionId);
    }
}
