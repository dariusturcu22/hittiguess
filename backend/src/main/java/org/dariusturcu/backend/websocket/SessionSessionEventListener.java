package org.dariusturcu.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GameSessionService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.Optional;

// The in-session-player half of disconnect handling story 11 deferred: registers a
// socket session against its game session on subscribe to the round topic, and uses that
// registration to flip the right Player's isConnected flag false (and, if that player was
// mid-turn, start their 90-second turn-timeout clock) on disconnect. Mirrors
// GroupSessionEventListener exactly, one level down at the session/Player layer instead
// of the group/Member layer.
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionSessionEventListener {

    private final SessionPresenceRegistry presenceRegistry;
    private final GameSessionService gameSessionService;

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        SessionDestinations.sessionIdFromRoundTopic(accessor.getDestination())
                .ifPresent(sessionId -> presenceRegistry.register(accessor.getSessionId(), sessionId));
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String stompSessionId = event.getSessionId();
        presenceRegistry.sessionIdFor(stompSessionId).ifPresent(sessionId ->
                extractUserId(event).ifPresent(userId -> disconnectQuietly(sessionId, userId)));
        presenceRegistry.remove(stompSessionId);
    }

    private void disconnectQuietly(Long sessionId, Long userId) {
        try {
            gameSessionService.disconnectPlayer(sessionId, userId);
        } catch (RuntimeException exception) {
            // The player, or the session itself, may already be gone (session ended or
            // abandoned) by the time the socket disconnect arrives.
            log.debug("Disconnect cleanup skipped for session {} user {}: {}", sessionId, userId, exception.getMessage());
        }
    }

    private Optional<Long> extractUserId(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return Optional.of(userPrincipal.getUser().getId());
        }
        return Optional.empty();
    }
}
