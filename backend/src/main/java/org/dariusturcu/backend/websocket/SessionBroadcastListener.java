package org.dariusturcu.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
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

    // After commit, not immediately: every client refetches state as soon as an event
    // arrives, and a broadcast sent mid-transaction lets that refetch read the state
    // from before the change. Publishers outside a transaction still broadcast at once.
    @TransactionalEventListener(fallbackExecution = true)
    public void onSessionBroadcastEvent(SessionBroadcastEvent event) {
        Long sessionId = event.sessionId();
        String destination = event.type() == SessionEventType.SESSION_ENDED
                ? SessionDestinations.endedTopic(sessionId)
                : SessionDestinations.roundTopic(sessionId);

        messagingTemplate.convertAndSend(destination, objectMapper.writeValueAsString(event));
        log.info("sessionEvent type={} sessionId={}", event.type(), sessionId);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onGuessResultEvent(GuessResultEvent event) {
        messagingTemplate.convertAndSendToUser(event.username(), SessionDestinations.guessResultQueue(event.sessionId()),
                objectMapper.writeValueAsString(event.result()));
    }
}
