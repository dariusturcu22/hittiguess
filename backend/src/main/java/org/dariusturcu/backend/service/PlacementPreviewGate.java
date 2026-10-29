package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundDTO;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.websocket.SessionBroadcastEvent;
import org.dariusturcu.backend.websocket.SessionEventType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// Keeps each active session's placement-preview state in memory so preview frames
// validate the sender, round status, and gap bounds without querying the database.
@Component
public class PlacementPreviewGate {

    public record Entry(Long roundId, Long activePlayerUserId, Long activePlayerId, int activePlayerTimelineSize) {
        public static Entry fromRound(Round round) {
            return new Entry(
                    round.getId(),
                    round.getActivePlayer().getUser().getId(),
                    round.getActivePlayer().getId(),
                    round.getActivePlayer().getTimeline().size());
        }
    }

    public sealed interface SessionState permits Open, Closed {
    }

    public record Open(Entry entry) implements SessionState {
    }

    public record Closed() implements SessionState {
    }

    private static final SessionState CLOSED_STATE = new Closed();

    private final Map<Long, SessionState> statesBySessionId = new ConcurrentHashMap<>();
    private final Map<Long, Entry> pendingEntriesBySessionId = new ConcurrentHashMap<>();
    private final Map<Long, Object> loadLocksBySessionId = new ConcurrentHashMap<>();

    public void stageRound(Long sessionId, Round round) {
        if (sessionId == null) {
            return;
        }
        if (round.getStatus() == RoundStatus.AWAITING_PLACEMENT) {
            pendingEntriesBySessionId.put(sessionId, Entry.fromRound(round));
        } else {
            pendingEntriesBySessionId.remove(sessionId);
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onSessionBroadcastEvent(SessionBroadcastEvent event) {
        Long sessionId = event.sessionId();
        if (sessionId == null) {
            return;
        }
        if (event.type() == SessionEventType.SESSION_ENDED) {
            pendingEntriesBySessionId.remove(sessionId);
            statesBySessionId.put(sessionId, CLOSED_STATE);
            loadLocksBySessionId.remove(sessionId);
            return;
        }
        if (!(event.payload() instanceof RoundDTO roundDTO)) {
            return;
        }
        if (roundDTO.status() != RoundStatus.AWAITING_PLACEMENT) {
            pendingEntriesBySessionId.remove(sessionId);
            statesBySessionId.put(sessionId, CLOSED_STATE);
            return;
        }
        Entry stagedEntry = pendingEntriesBySessionId.get(sessionId);
        if (stagedEntry != null) {
            statesBySessionId.put(sessionId, new Open(stagedEntry));
        }
    }

    public Optional<Entry> getOrLoad(Long sessionId, Supplier<Optional<Entry>> loader) {
        SessionState cachedState = statesBySessionId.get(sessionId);
        if (cachedState instanceof Open openState) {
            return Optional.of(openState.entry());
        }
        if (cachedState instanceof Closed) {
            return Optional.empty();
        }
        Object sessionLock = loadLocksBySessionId.computeIfAbsent(sessionId, ignoredSessionId -> new Object());
        synchronized (sessionLock) {
            SessionState recheckedState = statesBySessionId.get(sessionId);
            if (recheckedState instanceof Open openState) {
                return Optional.of(openState.entry());
            }
            if (recheckedState instanceof Closed) {
                return Optional.empty();
            }
            Optional<Entry> loadedEntry = loader.get();
            statesBySessionId.put(sessionId, loadedEntry.<SessionState>map(Open::new).orElse(CLOSED_STATE));
            return loadedEntry;
        }
    }
}
