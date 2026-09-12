package org.dariusturcu.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// Forwards every GroupBroadcastEvent GroupService publishes onto the matching STOMP
// topic. This is the one place group-side state events get logged on a per-group
// timeline: a structured line naming the event type and group id, in a shape a later
// addition (story 12's WebRTC connection lifecycle events) can log alongside using the
// same two fields, without needing a dedicated event-store table for this story.
//
// Serializes the event to JSON itself with the application's own ObjectMapper rather
// than sending the record through convertAndSend directly: Spring Boot's own STOMP
// message-broker configurer registers only the string and byte-array converters by
// default, no Jackson converter, so a raw POJO payload would have nothing able to
// convert it.
@Component
@RequiredArgsConstructor
@Slf4j
public class GroupBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @EventListener
    public void onGroupBroadcastEvent(GroupBroadcastEvent event) {
        Long groupId = event.group().id();
        String destination = event.type() == GroupEventType.SETTINGS_CHANGED
                ? GroupDestinations.settingsTopic(groupId)
                : GroupDestinations.membershipTopic(groupId);

        messagingTemplate.convertAndSend(destination, objectMapper.writeValueAsString(event));
        log.info("groupEvent type={} groupId={}", event.type(), groupId);
    }
}
