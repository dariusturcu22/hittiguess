package org.dariusturcu.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GroupService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.Optional;

// Registers a socket session against its group on subscribe, and uses that registration
// to flip the right member's isConnected flag false on disconnect. This is the group-side
// half of story 11's disconnect handling only: no in-session Player exists yet, story 10
// owns that half once its session model lands.
@Component
@RequiredArgsConstructor
@Slf4j
public class GroupSessionEventListener {

    private final GroupPresenceRegistry presenceRegistry;
    private final GroupService groupService;

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        GroupDestinations.groupIdFromMembershipTopic(accessor.getDestination())
                .ifPresent(groupId -> presenceRegistry.register(accessor.getSessionId(), groupId));
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        presenceRegistry.groupIdFor(sessionId).ifPresent(groupId ->
                extractUserId(event).ifPresent(userId -> disconnectQuietly(groupId, userId)));
        presenceRegistry.remove(sessionId);
    }

    private void disconnectQuietly(Long groupId, Long userId) {
        try {
            groupService.disconnectMember(groupId, userId);
        } catch (RuntimeException exception) {
            // The member may already be gone (explicit leave, group deleted) by the time
            // the socket disconnect arrives; there is nothing left to flip in that case.
            log.debug("Disconnect cleanup skipped for group {} user {}: {}", groupId, userId, exception.getMessage());
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
