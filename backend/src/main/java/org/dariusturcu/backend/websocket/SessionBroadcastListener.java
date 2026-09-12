package org.dariusturcu.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// Forwards every SessionBroadcastEvent GameSessionService publishes onto the matching
// STOMP topic, the session-side counterpart to GroupBroadcastListener. Logs a structured
// line per event on the same two-field shape GroupBroadcastListener already established
// (type, id), so both timelines can be correlated the same way.
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @EventListener
    public void onSessionBroadcastEvent(SessionBroadcastEvent event) {
        Long sessionId = event.sessionId();
        String destination = event.type() == SessionEventType.SESSION_ENDED
                ? SessionDestinations.endedTopic(sessionId)
                : SessionDestinations.roundTopic(sessionId);

        messagingTemplate.convertAndSend(destination, objectMapper.writeValueAsString(event));
        log.info("sessionEvent type={} sessionId={}", event.type(), sessionId);
    }
}
