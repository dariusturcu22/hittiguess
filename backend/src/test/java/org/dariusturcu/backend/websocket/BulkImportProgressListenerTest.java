package org.dariusturcu.backend.websocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BulkImportProgressListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private BulkImportProgressListener listener;

    @Test
    void routesTheEventToTheSubmittingUsersOwnProgressQueueWithTheEventPayload() {
        listener = new BulkImportProgressListener(messagingTemplate, objectMapper);

        listener.onBulkImportProgressEvent(
                new BulkImportProgressEvent("some-user", "job-1", "video-id-1", BulkImportProgressOutcome.RESOLVED));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("some-user"), eq(BulkImportDestinations.progressQueue()), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("video-id-1").contains("RESOLVED");
    }

    @Test
    void targetsOnlyTheEventsOwnUsernameNotAAnyGroupOrSessionTopic() {
        listener = new BulkImportProgressListener(messagingTemplate, objectMapper);

        listener.onBulkImportProgressEvent(
                new BulkImportProgressEvent("first-user", "job-a", "video-id-a", BulkImportProgressOutcome.ALREADY_KNOWN));
        listener.onBulkImportProgressEvent(
                new BulkImportProgressEvent("second-user", "job-b", "video-id-b", BulkImportProgressOutcome.UNRESOLVED));

        verify(messagingTemplate).convertAndSendToUser(
                eq("first-user"), eq(BulkImportDestinations.progressQueue()), org.mockito.ArgumentMatchers.contains("video-id-a"));
        verify(messagingTemplate).convertAndSendToUser(
                eq("second-user"), eq(BulkImportDestinations.progressQueue()), org.mockito.ArgumentMatchers.contains("video-id-b"));
    }
}
