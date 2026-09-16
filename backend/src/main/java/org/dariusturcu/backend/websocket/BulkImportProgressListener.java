package org.dariusturcu.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// Forwards every BulkImportProgressEvent BulkImportService publishes to the submitting
// user's own per-user STOMP queue, the per-user counterpart to GroupBroadcastListener
// and SessionBroadcastListener. convertAndSendToUser resolves the destination against
// whichever session(s) that username's STOMP CONNECT already authenticated (see
// StompAuthenticationChannelInterceptor), the same way convertAndSend resolves a topic
// against its subscribers.
//
// Serializes the event to JSON itself with the application's own ObjectMapper for the
// same reason GroupBroadcastListener does: the STOMP broker registers only the string
// and byte-array converters by default, no Jackson converter.
@Component
@RequiredArgsConstructor
@Slf4j
public class BulkImportProgressListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @EventListener
    public void onBulkImportProgressEvent(BulkImportProgressEvent event) {
        messagingTemplate.convertAndSendToUser(
                event.username(), BulkImportDestinations.progressQueue(), objectMapper.writeValueAsString(event));
        log.info("bulkImportProgress username={} youtubeId={} outcome={}",
                event.username(), event.youtubeId(), event.outcome());
    }
}
