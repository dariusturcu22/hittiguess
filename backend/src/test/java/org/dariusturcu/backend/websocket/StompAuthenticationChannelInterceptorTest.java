package org.dariusturcu.backend.websocket;

import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthenticationChannelInterceptorTest {

    private static final String USER_EMAIL = "handshake-user@example.test";

    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private MessageChannel channel;

    private JwtUtil jwtUtil;
    private StompAuthenticationChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-handshake-secret-test-handshake-secret");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 900000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604800000L);

        interceptor = new StompAuthenticationChannelInterceptor(jwtUtil, userDetailsService);
    }

    private User userWithEmail(String email) {
        User user = new User();
        user.setId(1L);
        user.setUsername("handshake-user");
        user.setEmail(email);
        user.setRole(Role.USER);
        return user;
    }

    private Message<byte[]> connectMessageWithAuthorization(String headerValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (headerValue != null) {
            accessor.addNativeHeader("Authorization", headerValue);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void aValidTokenAuthenticatesAndSetsTheConnectedUser() {
        User user = userWithEmail(USER_EMAIL);
        UserPrincipal principal = new UserPrincipal(user);
        when(userDetailsService.loadUserByUsername(USER_EMAIL)).thenReturn(principal);
        String token = jwtUtil.generateToken(user);

        Message<?> result = interceptor.preSend(connectMessageWithAuthorization("Bearer " + token), channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNotNull();
    }

    @Test
    void aMissingAuthorizationHeaderIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessageWithAuthorization(null), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void anAuthorizationHeaderWithoutTheBearerPrefixIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessageWithAuthorization("not-bearer-token"), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void aMalformedTokenIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessageWithAuthorization("Bearer not-a-real-jwt"), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void aTokenWhoseUserCannotBeFoundIsRejected() {
        User user = userWithEmail(USER_EMAIL);
        String token = jwtUtil.generateToken(user);
        when(userDetailsService.loadUserByUsername(eq(USER_EMAIL)))
                .thenThrow(new UsernameNotFoundException("User not found"));

        assertThatThrownBy(() -> interceptor.preSend(connectMessageWithAuthorization("Bearer " + token), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void aTokenThatFailsValidationAgainstTheLoadedUserIsRejected() {
        User tokenSubject = userWithEmail(USER_EMAIL);
        String token = jwtUtil.generateToken(tokenSubject);
        // Simulates the account having changed underneath the token between issuance and
        // use: the email loaded back no longer matches what the token was signed for.
        User differentAccountState = userWithEmail("changed-since-issuance@example.test");
        when(userDetailsService.loadUserByUsername(USER_EMAIL)).thenReturn(new UserPrincipal(differentAccountState));

        assertThatThrownBy(() -> interceptor.preSend(connectMessageWithAuthorization("Bearer " + token), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void nonConnectCommandsPassThroughWithoutAuthentication() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/groups/1/admin");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }
}
