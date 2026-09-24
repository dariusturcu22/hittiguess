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
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionSessionEventListenerTest {

    private static final Long USER_ID = 11L;
    private static final Long SESSION_ID = 3L;
    private static final String STOMP_SESSION_ID = "stomp-session";

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
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(STOMP_SESSION_ID);
        accessor.setDestination(SessionDestinations.roundTopic(SESSION_ID));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionSubscribeEvent(this, message, subscriber);
    }

    @Test
    void aPlayersRoundSubscriptionRegistersPresence() {
        when(playerRepository.existsBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(true);

        listener.handleSubscribe(subscribeToRoundTopic());

        assertThat(presenceRegistry.sessionIdFor(STOMP_SESSION_ID)).contains(SESSION_ID);
    }

    @Test
    void aNonPlayersRoundSubscriptionRegistersNothing() {
        when(playerRepository.existsBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(false);

        listener.handleSubscribe(subscribeToRoundTopic());

        assertThat(presenceRegistry.sessionIdFor(STOMP_SESSION_ID)).isEmpty();
    }
}
