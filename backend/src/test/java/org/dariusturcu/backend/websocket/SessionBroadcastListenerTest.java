package org.dariusturcu.backend.websocket;

import org.dariusturcu.backend.model.session.GuessResultDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// Clients refetch the session the moment an event arrives, so the broadcast has to wait
// until the change it announces is committed.
class SessionBroadcastListenerTest {

    private static final Long SESSION_ID = 7L;

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    SessionBroadcastListenerTest() {
        context.registerBean(SimpMessagingTemplate.class, () -> messagingTemplate);
        context.registerBean(ObjectMapper.class, () -> JsonMapper.builder().build());
        context.registerBean(TransactionalEventListenerFactory.class);
        context.registerBean(SessionBroadcastListener.class);
        context.refresh();
    }

    @AfterEach
    void closeContextAndTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        context.close();
    }

    @Test
    void anEventPublishedInsideATransactionIsBroadcastOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        context.publishEvent(new SessionBroadcastEvent(SessionEventType.BETTING_OPENED, SESSION_ID, "round"));

        verify(messagingTemplate, never()).convertAndSend(anyString(), anyString());

        TransactionSynchronizationUtils.invokeAfterCompletion(
                TransactionSynchronizationManager.getSynchronizations(), TransactionSynchronization.STATUS_COMMITTED);

        verify(messagingTemplate).convertAndSend(eq(SessionDestinations.roundTopic(SESSION_ID)), anyString());
    }

    @Test
    void anEventPublishedOutsideATransactionIsBroadcastImmediately() {
        context.publishEvent(new SessionBroadcastEvent(SessionEventType.PLACEMENT_PREVIEW, SESSION_ID, "preview"));

        verify(messagingTemplate).convertAndSend(eq(SessionDestinations.roundTopic(SESSION_ID)), anyString());
    }

    @Test
    void aGuessResultIsSentOnlyToTheGuessersOwnQueue() {
        String guesserUsername = "guesser";
        long roundId = 3L;

        context.publishEvent(new GuessResultEvent(guesserUsername, SESSION_ID, new GuessResultDTO(roundId, true, false)));

        verify(messagingTemplate).convertAndSendToUser(eq(guesserUsername), eq(SessionDestinations.guessResultQueue(SESSION_ID)), anyString());
        verify(messagingTemplate, never()).convertAndSend(anyString(), anyString());
    }
}
