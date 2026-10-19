package org.dariusturcu.backend.websocket;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PlayerRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;

// Authorizes every STOMP SUBSCRIBE: a group topic needs group membership, a session
// topic needs a player in that session, and a "/user" destination is always allowed
// because Spring resolves it to the subscriber's own session. Anything else, including a
// direct subscription to a resolved per-user "/queue" destination, is refused. Throwing
// here keeps the subscription from being registered and sends the client an ERROR frame.
@Component
@RequiredArgsConstructor
public class StompSubscriptionAuthorizationInterceptor implements ChannelInterceptor {

    private static final String USER_DESTINATION_PREFIX = "/user/";

    private final MemberRepository memberRepository;
    private final PlayerRepository playerRepository;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                && !isAllowed(accessor.getDestination(), accessor.getUser())) {
            throw new MessagingException("Not allowed to subscribe to " + accessor.getDestination());
        }
        return message;
    }

    private boolean isAllowed(String destination, Principal principal) {
        Optional<Long> userId = userIdOf(principal);
        if (destination == null || userId.isEmpty()) {
            return false;
        }
        if (destination.startsWith(USER_DESTINATION_PREFIX)) {
            return true;
        }
        Optional<Long> groupId = GroupDestinations.groupIdFromTopic(destination);
        if (groupId.isPresent()) {
            return memberRepository.existsByGroupIdAndUserId(groupId.get(), userId.get());
        }
        Optional<Long> sessionId = SessionDestinations.sessionIdFromTopic(destination);
        return sessionId.isPresent() && playerRepository.existsBySessionIdAndUserId(sessionId.get(), userId.get());
    }

    private Optional<Long> userIdOf(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return Optional.of(userPrincipal.getUser().getId());
        }
        return Optional.empty();
    }
}
