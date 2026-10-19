package org.dariusturcu.backend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Closes every open socket of one user. A STOMP subscription is authorized only when it
// is made, so this is how access that was later revoked stops reaching the user: their
// client reconnects, and each subscription has to pass the check again. Tracks the open
// WebSocket sessions itself, keyed by the id STOMP uses as its session id, and finds a
// user's sessions through the SimpUserRegistry.
@Slf4j
@Component
public class UserSocketCloser implements WebSocketHandlerDecoratorFactory {

    private final Map<String, WebSocketSession> openSocketsById = new ConcurrentHashMap<>();
    private final SimpUserRegistry simpUserRegistry;

    public UserSocketCloser(@Lazy SimpUserRegistry simpUserRegistry) {
        this.simpUserRegistry = simpUserRegistry;
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                openSocketsById.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                openSocketsById.remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    public void closeSocketsOf(String principalName) {
        SimpUser user = simpUserRegistry.getUser(principalName);
        if (user == null) {
            return;
        }
        for (SimpSession stompSession : user.getSessions()) {
            WebSocketSession socket = openSocketsById.get(stompSession.getId());
            if (socket == null) {
                continue;
            }
            try {
                socket.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException closeFailure) {
                log.warn("Could not close socket {} of a user whose access was revoked", socket.getId(), closeFailure);
            }
        }
    }
}
