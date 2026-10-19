package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.model.voice.VoiceSignalRequest;
import org.dariusturcu.backend.model.voice.VoiceSignalType;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GroupService;
import org.dariusturcu.backend.websocket.GroupDestinations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceSignalingControllerTest {

    private static final Long GROUP_ID = 7L;
    private static final Long SENDER_USER_ID = 11L;
    private static final Long TARGET_USER_ID = 12L;

    @Mock
    private GroupService groupService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private VoiceSignalingController controller() {
        return new VoiceSignalingController(groupService, userRepository, messagingTemplate, objectMapper);
    }

    private User userWithId(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("member-" + userId);
        user.setRole(Role.USER);
        return user;
    }

    private Principal principalFor(Long userId) {
        return new UsernamePasswordAuthenticationToken(new UserPrincipal(userWithId(userId)), null, null);
    }

    @Test
    void aMembersOfferAnswerAndCandidateEachReachOnlyTheTargetsOwnQueue() {
        when(groupService.isGroupMember(GROUP_ID, SENDER_USER_ID)).thenReturn(true);
        when(groupService.isGroupMember(GROUP_ID, TARGET_USER_ID)).thenReturn(true);
        User target = userWithId(TARGET_USER_ID);
        when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(target));
        Principal sender = principalFor(SENDER_USER_ID);

        for (VoiceSignalType type : VoiceSignalType.values()) {
            controller().relaySignal(
                    GROUP_ID,
                    new VoiceSignalRequest(type, TARGET_USER_ID, "opaque-" + type),
                    sender);
        }

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, org.mockito.Mockito.times(VoiceSignalType.values().length))
                .convertAndSendToUser(eq(target.getUsername()), eq(GroupDestinations.voiceSignalQueue(GROUP_ID)), payloadCaptor.capture());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

        assertThat(payloadCaptor.getAllValues())
                .anySatisfy(payload -> assertThat(payload).contains("OFFER"))
                .anySatisfy(payload -> assertThat(payload).contains("ANSWER"))
                .anySatisfy(payload -> assertThat(payload).contains("CANDIDATE"));
        assertThat(payloadCaptor.getAllValues())
                .allSatisfy(payload -> assertThat(payload)
                        .contains("\"senderUserId\":" + SENDER_USER_ID)
                        .contains("\"targetMemberUserId\":" + TARGET_USER_ID));
    }

    @Test
    void aNonMembersSignalIsRejectedAndNothingIsRelayed() {
        when(groupService.isGroupMember(GROUP_ID, SENDER_USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> controller().relaySignal(
                GROUP_ID,
                new VoiceSignalRequest(VoiceSignalType.OFFER, TARGET_USER_ID, "opaque"),
                principalFor(SENDER_USER_ID)))
                .isInstanceOf(AccessDeniedException.class);

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }

    @Test
    void aSignalToSomeoneOutsideTheGroupIsRejectedAndNothingIsRelayed() {
        when(groupService.isGroupMember(GROUP_ID, SENDER_USER_ID)).thenReturn(true);
        when(groupService.isGroupMember(GROUP_ID, TARGET_USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> controller().relaySignal(
                GROUP_ID,
                new VoiceSignalRequest(VoiceSignalType.OFFER, TARGET_USER_ID, "opaque"),
                principalFor(SENDER_USER_ID)))
                .isInstanceOf(AccessDeniedException.class);

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }

    @Test
    void anUnauthenticatedSignalIsRejected() {
        assertThatThrownBy(() -> controller().relaySignal(
                GROUP_ID,
                new VoiceSignalRequest(VoiceSignalType.OFFER, TARGET_USER_ID, "opaque"),
                new UnknownPrincipal()))
                .isInstanceOf(AccessDeniedException.class);

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }

    private static final class UnknownPrincipal implements Principal {
        @Override
        public String getName() {
            return "not-an-authentication";
        }
    }
}
