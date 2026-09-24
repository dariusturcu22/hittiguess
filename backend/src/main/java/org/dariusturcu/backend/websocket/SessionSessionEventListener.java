package org.dariusturcu.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.repository.PlayerRepository;
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

// The in-session-player half of disconnect handling story 11 deferred: a player's socket
// subscribing to the round topic registers it and marks the player connected again, and
// once that player's last registered socket for the session closes, their isConnected
// flag flips false (and, if they were mid-turn, their 90-second turn-timeout clock
// starts). The session/Player-layer counterpart to GroupSessionEventListener.
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionSessionEventListener {

    private final SessionPresenceRegistry presenceRegistry;
    private final GameSessionService gameSessionService;
    private final PlayerRepository playerRepository;

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Optional<Long> userId = extractUserId(event.getUser());
        SessionDestinations.sessionIdFromRoundTopic(accessor.getDestination())
                .filter(sessionId -> userId.isPresent() && playerRepository.existsBySessionIdAndUserId(sessionId, userId.get()))
                .ifPresent(sessionId -> {
                    presenceRegistry.register(accessor.getSessionId(), sessionId, userId.get());
                    reconnectQuietly(sessionId, userId.get());
                });
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        presenceRegistry.remove(event.getSessionId())
                .filter(playerSocket -> !presenceRegistry.hasOpenSocket(playerSocket.sessionId(), playerSocket.userId()))
                .ifPresent(playerSocket -> disconnectQuietly(playerSocket.sessionId(), playerSocket.userId()));
    }

    private void reconnectQuietly(Long sessionId, Long userId) {
        try {
            gameSessionService.reconnectPlayer(sessionId, userId);
        } catch (RuntimeException exception) {
            // The session may have ended between the subscription and this callback.
            log.debug("Reconnect skipped for session {} user {}: {}", sessionId, userId, exception.getMessage());
        }
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

    private Optional<Long> extractUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return Optional.of(userPrincipal.getUser().getId());
        }
        return Optional.empty();
    }
}
