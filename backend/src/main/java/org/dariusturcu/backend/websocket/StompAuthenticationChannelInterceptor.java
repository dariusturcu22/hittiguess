package org.dariusturcu.backend.websocket;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

// Authenticates every STOMP CONNECT frame against the same JWT the REST side validates,
// following Spring's documented pattern for token-based STOMP authentication: a
// ChannelInterceptor on the client inbound channel reads the token from the CONNECT
// frame's own headers and calls StompHeaderAccessor#setUser, rather than relying on the
// HTTP handshake's session or cookies. A browser WebSocket can't set arbitrary HTTP
// request headers on the handshake itself, so the client sends the same "Authorization:
// Bearer <token>" value as a native STOMP header on CONNECT instead.
//
// Throwing from preSend on CONNECT is the standard way to reject a STOMP connection:
// Spring sends the client a STOMP ERROR frame and closes the session, no separate
// rejection path is needed.
@Component
@RequiredArgsConstructor
public class StompAuthenticationChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor));
        }
        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        if (token == null) {
            throw new MessagingException("Missing authentication token on STOMP CONNECT");
        }

        try {
            String email = jwtUtil.extractEmail(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (!jwtUtil.validateToken(token, userDetails)) {
                throw new MessagingException("Invalid or expired authentication token");
            }
            return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        } catch (MessagingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MessagingException("WebSocket authentication failed", exception);
        }
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
