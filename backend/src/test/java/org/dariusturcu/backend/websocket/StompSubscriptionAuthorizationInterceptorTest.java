package org.dariusturcu.backend.websocket;

import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PlayerRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompSubscriptionAuthorizationInterceptorTest {

    private static final Long USER_ID = 11L;
    private static final Long GROUP_ID = 7L;
    private static final Long SESSION_ID = 3L;
    private static final String USER_DESTINATION_PREFIX = "/user";

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private MessageChannel channel;

    private StompSubscriptionAuthorizationInterceptor interceptor;
    private Principal subscriber;

    @BeforeEach
    void setUp() {
        interceptor = new StompSubscriptionAuthorizationInterceptor(memberRepository, playerRepository);
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("subscriber");
        user.setRole(Role.USER);
        subscriber = new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null);
    }

    private Message<byte[]> frame(StompCommand command, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeFrame(String destination) {
        return frame(StompCommand.SUBSCRIBE, destination, subscriber);
    }

    @Test
    void aMemberMaySubscribeToTheirGroupsTopics() {
        when(memberRepository.existsByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(true);
        Message<byte[]> message = subscribeFrame(GroupDestinations.chatTopic(GROUP_ID));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void aNonMemberIsRefusedAGroupTopic() {
        when(memberRepository.existsByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(subscribeFrame(GroupDestinations.voiceTopic(GROUP_ID)), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void aPlayerMaySubscribeToTheirSessionsTopics() {
        when(playerRepository.existsBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(true);
        Message<byte[]> message = subscribeFrame(SessionDestinations.roundTopic(SESSION_ID));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void aNonPlayerIsRefusedASessionTopic() {
        when(playerRepository.existsBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(subscribeFrame(SessionDestinations.endedTopic(SESSION_ID)), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void aUserDestinationIsAlwaysAllowed() {
        Message<byte[]> message = subscribeFrame(USER_DESTINATION_PREFIX + GroupDestinations.voiceSignalQueue(GROUP_ID));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        verify(memberRepository, never()).existsByGroupIdAndUserId(anyLong(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/queue/groups/7/voice-signal-usersession1", "/topic/other", "/topic/groups/7/chat/extra", "/app/groups/7/chat"})
    void anyOtherDestinationIsRefused(String destination) {
        assertThatThrownBy(() -> interceptor.preSend(subscribeFrame(destination), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void aSubscriptionWithoutAnAuthenticatedUserIsRefused() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, GroupDestinations.chatTopic(GROUP_ID), null), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void framesOtherThanSubscribePassThrough() {
        Message<byte[]> message = frame(StompCommand.SEND, GroupDestinations.chatDestination(GROUP_ID), null);

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }
}
