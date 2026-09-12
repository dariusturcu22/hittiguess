package org.dariusturcu.backend.service;

import org.dariusturcu.backend.websocket.GroupBroadcastEvent;
import org.dariusturcu.backend.websocket.GroupEventType;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Wires GameSessionService into GroupService's existing start-session state transition
// without duplicating it: GroupService.startGameSession already flips the group to
// LOCKED and publishes GAME_SESSION_STARTED inside its own transaction; this listener
// reacts to that same event and creates the actual GameSession, joining the same
// transaction so the whole operation (group lock plus session creation) commits or rolls
// back together.
@Component
@RequiredArgsConstructor
public class GameSessionStartListener {

    private final GameSessionService gameSessionService;

    @EventListener
    public void onGroupBroadcastEvent(GroupBroadcastEvent event) {
        if (event.type() == GroupEventType.GAME_SESSION_STARTED) {
            gameSessionService.startSession(event.group().id());
        }
    }
}
