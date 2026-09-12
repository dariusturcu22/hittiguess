package org.dariusturcu.backend.websocket;

import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.GroupStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GroupBroadcastListenerTest {

    private static final int MEMBERSHIP_TOPIC_EVENT_TYPE_COUNT = 4;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private GroupBroadcastListener listener;

    private GroupDetailDTO groupDetail(Long groupId) {
        return new GroupDetailDTO(
                groupId, "invite-code", "ABCD", GroupStatus.OPEN, DjMode.FIXED, 5,
                List.of(), List.of(), null);
    }

    @Test
    void memberJoinedRoutesToTheMembershipTopicWithTheEventTypeAndGroupInThePayload() {
        listener = new GroupBroadcastListener(messagingTemplate, objectMapper);
        GroupDetailDTO group = groupDetail(1L);

        listener.onGroupBroadcastEvent(new GroupBroadcastEvent(GroupEventType.MEMBER_JOINED, group));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSend(eq(GroupDestinations.membershipTopic(1L)), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("MEMBER_JOINED").contains("\"id\":1");
    }

    @Test
    void settingsChangedRoutesToTheSettingsTopic() {
        listener = new GroupBroadcastListener(messagingTemplate, objectMapper);
        GroupDetailDTO group = groupDetail(2L);

        listener.onGroupBroadcastEvent(new GroupBroadcastEvent(GroupEventType.SETTINGS_CHANGED, group));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSend(eq(GroupDestinations.settingsTopic(2L)), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("SETTINGS_CHANGED");
    }

    @Test
    void memberLeftAdminChangedConnectionChangedAndSessionStartedAllRouteToTheMembershipTopic() {
        listener = new GroupBroadcastListener(messagingTemplate, objectMapper);
        GroupDetailDTO group = groupDetail(3L);
        String membershipTopic = GroupDestinations.membershipTopic(3L);

        listener.onGroupBroadcastEvent(new GroupBroadcastEvent(GroupEventType.MEMBER_LEFT, group));
        listener.onGroupBroadcastEvent(new GroupBroadcastEvent(GroupEventType.ADMIN_CHANGED, group));
        listener.onGroupBroadcastEvent(new GroupBroadcastEvent(GroupEventType.MEMBER_CONNECTION_CHANGED, group));
        listener.onGroupBroadcastEvent(new GroupBroadcastEvent(GroupEventType.GAME_SESSION_STARTED, group));

        verify(messagingTemplate, times(MEMBERSHIP_TOPIC_EVENT_TYPE_COUNT)).convertAndSend(eq(membershipTopic), anyString());
    }
}
