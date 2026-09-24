package org.dariusturcu.backend.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSocketCloserTest {

    private static final String REMOVED_USER = "removed-user";
    private static final String OPEN_SOCKET_ID = "open-socket";
    private static final String CLOSED_SOCKET_ID = "closed-socket";

    @Mock
    private SimpUserRegistry simpUserRegistry;
    @Mock
    private WebSocketHandler innerHandler;

    private UserSocketCloser userSocketCloser;
    private WebSocketHandler decoratedHandler;

    @BeforeEach
    void setUp() {
        userSocketCloser = new UserSocketCloser(simpUserRegistry);
        decoratedHandler = userSocketCloser.decorate(innerHandler);
    }

    private WebSocketSession socketWithId(String socketId) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(socketId);
        return socket;
    }

    private SimpSession stompSessionWithId(String sessionId) {
        SimpSession stompSession = mock(SimpSession.class);
        when(stompSession.getId()).thenReturn(sessionId);
        return stompSession;
    }

    @Test
    void closesEveryStillOpenSocketOfTheUser() throws Exception {
        WebSocketSession openSocket = socketWithId(OPEN_SOCKET_ID);
        WebSocketSession closedSocket = socketWithId(CLOSED_SOCKET_ID);
        decoratedHandler.afterConnectionEstablished(openSocket);
        decoratedHandler.afterConnectionEstablished(closedSocket);
        decoratedHandler.afterConnectionClosed(closedSocket, CloseStatus.NORMAL);
        SimpUser removedUser = mock(SimpUser.class);
        SimpSession openStompSession = stompSessionWithId(OPEN_SOCKET_ID);
        SimpSession closedStompSession = stompSessionWithId(CLOSED_SOCKET_ID);
        when(removedUser.getSessions()).thenReturn(Set.of(openStompSession, closedStompSession));
        when(simpUserRegistry.getUser(REMOVED_USER)).thenReturn(removedUser);

        userSocketCloser.closeSocketsOf(REMOVED_USER);

        verify(openSocket).close(CloseStatus.POLICY_VIOLATION);
        verify(closedSocket, never()).close(any());
    }

    @Test
    void aUserWithNoConnectionIsANoOp() {
        when(simpUserRegistry.getUser(REMOVED_USER)).thenReturn(null);

        userSocketCloser.closeSocketsOf(REMOVED_USER);

        verify(simpUserRegistry).getUser(REMOVED_USER);
    }
}
