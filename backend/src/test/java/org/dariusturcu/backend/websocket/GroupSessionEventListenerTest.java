package org.dariusturcu.backend.websocket;

import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GroupService;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GroupSessionEventListenerTest {

    private static final String SESSION_ID = "session-1";

    @Mock
    private GroupService groupService;

    private GroupPresenceRegistry presenceRegistry;
    private GroupSessionEventListener listener;

    @BeforeEach
    void setUp() {
        presenceRegistry = new GroupPresenceRegistry();
        listener = new GroupSessionEventListener(presenceRegistry, groupService);
    }

    private Message<byte[]> subscribeMessage(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> disconnectMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(SESSION_ID);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Authentication authenticationFor(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("member-" + userId);
        user.setRole(Role.USER);
        return new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null);
    }

    @Test
    void subscribingToAGroupsMembershipTopicRegistersTheSession() {
        SessionSubscribeEvent event = new SessionSubscribeEvent(
                this, subscribeMessage(GroupDestinations.membershipTopic(42L)));

        listener.handleSubscribe(event);

        assertThatSessionIsRegisteredFor(42L);
    }

    @Test
    void subscribingToAnUnrelatedDestinationRegistersNothing() {
        SessionSubscribeEvent event = new SessionSubscribeEvent(
                this, subscribeMessage(GroupDestinations.chatTopic(42L)));

        listener.handleSubscribe(event);

        assertThatNoSessionIsRegistered();
    }

    @Test
    void disconnectingARegisteredSessionDisconnectsTheRightMemberAndClearsTheRegistration() {
        presenceRegistry.register(SESSION_ID, 42L);
        Authentication authentication = authenticationFor(7L);
        SessionDisconnectEvent event = new SessionDisconnectEvent(
                this, disconnectMessage(), SESSION_ID, CloseStatus.NORMAL, authentication);

        listener.handleDisconnect(event);

        verify(groupService).disconnectMember(42L, 7L);
        assertThatNoSessionIsRegistered();
    }

    @Test
    void disconnectingAnUnregisteredSessionCallsNothing() {
        Authentication authentication = authenticationFor(7L);
        SessionDisconnectEvent event = new SessionDisconnectEvent(
                this, disconnectMessage(), SESSION_ID, CloseStatus.NORMAL, authentication);

        listener.handleDisconnect(event);

        verifyNoInteractions(groupService);
    }

    @Test
    void disconnectingASessionWithNoAuthenticatedUserSkipsTheGroupServiceCall() {
        presenceRegistry.register(SESSION_ID, 42L);
        SessionDisconnectEvent event = new SessionDisconnectEvent(
                this, disconnectMessage(), SESSION_ID, CloseStatus.NORMAL);

        listener.handleDisconnect(event);

        verifyNoInteractions(groupService);
        assertThatNoSessionIsRegistered();
    }

    private void assertThatSessionIsRegisteredFor(Long groupId) {
        Optional<Long> registered = presenceRegistry.groupIdFor(SESSION_ID);
        assertThat(registered).contains(groupId);
    }

    private void assertThatNoSessionIsRegistered() {
        assertThat(presenceRegistry.groupIdFor(SESSION_ID)).isEmpty();
    }
}
