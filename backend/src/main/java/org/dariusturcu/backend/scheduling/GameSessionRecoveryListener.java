package org.dariusturcu.backend.scheduling;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.service.GameSessionService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Wires GameSessionService's restart recovery to application startup, the same thin
// shape as the sweepers: the recovery itself is callable directly in a test.
@Component
@RequiredArgsConstructor
public class GameSessionRecoveryListener {

    private final GameSessionService gameSessionService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInProgressSessions() {
        gameSessionService.recoverInProgressSessions();
    }
}
