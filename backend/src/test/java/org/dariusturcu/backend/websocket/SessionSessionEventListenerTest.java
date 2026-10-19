package org.dariusturcu.backend.websocket;

import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlayerRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GameSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionSessionEventListenerTest {

    private static final Long USER_ID = 11L;
    private static final Long SESSION_ID = 3L;
    private static final String STOMP_SESSION_ID = "stomp-session";
    private static final String SECOND_STOMP_SESSION_ID = "second-stomp-session";

    @Mock
    private GameSessionService gameSessionService;
    @Mock
    private PlayerRepository playerRepository;

    private final SessionPresenceRegistry presenceRegistry = new SessionPresenceRegistry();
    private SessionSessionEventListener listener;
    private Authentication subscriber;

    @BeforeEach
    void setUp() {
        listener = new SessionSessionEventListener(presenceRegistry, gameSessionService, playerRepository);
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("subscriber");
        user.setRole(Role.USER);
        subscriber = new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null);
    }

    private SessionSubscribeEvent subscribeToRoundTopic() {
        return subscribeToRoundTopic(STOMP_SESSION_ID);
    }

    private SessionSubscribeEvent subscribeToRoundTopic(String stompSessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(stompSessionId);
        accessor.setDestination(SessionDestinations.roundTopic(SESSION_ID));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionSubscribeEvent(this, message, subscriber);
    }

    private SessionDisconnectEvent socketCloses(String stompSessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(stompSessionId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, stompSessionId, CloseStatus.NORMAL, subscriber);
    }

    @Test
    void aPlayersRoundSubscriptionRegistersPresenceAndReconnectsThePlayer() {
        when(playerRepository.existsBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(true);

        listener.handleSubscribe(subscribeToRoundTopic());

        assertThat(presenceRegistry.hasOpenSocket(SESSION_ID, USER_ID)).isTrue();
        verify(gameSessionService).reconnectPlayer(SESSION_ID, USER_ID);
    }

    @Test
    void aNonPlayersRoundSubscriptionRegistersNothing() {
        when(playerRepository.existsBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(false);

        listener.handleSubscribe(subscribeToRoundTopic());

        assertThat(presenceRegistry.hasOpenSocket(SESSION_ID, USER_ID)).isFalse();
        verify(gameSessionService, never()).reconnectPlayer(SESSION_ID, USER_ID);
    }

    @Test
    void closingOneOfSeveralSocketsLeavesThePlayerConnected() {
        when(playerRepository.existsBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(true);
        listener.handleSubscribe(subscribeToRoundTopic(STOMP_SESSION_ID));
        listener.handleSubscribe(subscribeToRoundTopic(SECOND_STOMP_SESSION_ID));

        listener.handleDisconnect(socketCloses(STOMP_SESSION_ID));

        verify(gameSessionService, never()).disconnectPlayer(SESSION_ID, USER_ID);
        assertThat(presenceRegistry.hasOpenSocket(SESSION_ID, USER_ID)).isTrue();
    }

    @Test
    void closingThePlayersLastSocketDisconnectsThem() {
        when(playerRepository.existsBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(true);
        listener.handleSubscribe(subscribeToRoundTopic(STOMP_SESSION_ID));
        listener.handleSubscribe(subscribeToRoundTopic(SECOND_STOMP_SESSION_ID));

        listener.handleDisconnect(socketCloses(STOMP_SESSION_ID));
        listener.handleDisconnect(socketCloses(SECOND_STOMP_SESSION_ID));

        verify(gameSessionService).disconnectPlayer(SESSION_ID, USER_ID);
    }

    @Test
    void closingASocketThatNeverSubscribedAsAPlayerDisconnectsNobody() {
        listener.handleDisconnect(socketCloses(STOMP_SESSION_ID));

        verify(gameSessionService, never()).disconnectPlayer(SESSION_ID, USER_ID);
    }
}
