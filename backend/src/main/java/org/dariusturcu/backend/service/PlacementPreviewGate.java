package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundDTO;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.websocket.SessionBroadcastEvent;
import org.dariusturcu.backend.websocket.SessionEventType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// Keeps each active session's placement-preview state in memory so preview frames
// validate the sender, round status, and gap bounds without querying the database.
@Component
public class PlacementPreviewGate {

    public static final Duration CACHE_MISS_CLOSED_TTL = Duration.ofMinutes(1);

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

    public record Closed(Instant expiresAt) implements SessionState {
        public Closed() {
            this(null);
        }

        public boolean isExpired(Instant now) {
            return expiresAt != null && now.isAfter(expiresAt);
        }
    }

    private static final SessionState CLOSED_BETWEEN_ROUNDS = new Closed();

    private final Clock clock;
    private final Map<Long, SessionState> statesBySessionId = new ConcurrentHashMap<>();
    private final Map<Long, Entry> pendingEntriesBySessionId = new ConcurrentHashMap<>();
    private final Map<Long, Object> loadLocksBySessionId = new ConcurrentHashMap<>();

    public PlacementPreviewGate() {
        this(Clock.systemUTC());
    }

    public PlacementPreviewGate(Clock clock) {
        this.clock = clock;
    }

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
            statesBySessionId.remove(sessionId);
            loadLocksBySessionId.remove(sessionId);
            return;
        }
        if (!(event.payload() instanceof RoundDTO roundDTO)) {
            return;
        }
        if (roundDTO.status() != RoundStatus.AWAITING_PLACEMENT) {
            pendingEntriesBySessionId.remove(sessionId);
            statesBySessionId.put(sessionId, CLOSED_BETWEEN_ROUNDS);
            return;
        }
        Entry stagedEntry = pendingEntriesBySessionId.get(sessionId);
        if (stagedEntry != null) {
            statesBySessionId.put(sessionId, new Open(stagedEntry));
        }
    }

    public Optional<Entry> getOrLoad(Long sessionId, Supplier<Optional<Entry>> loader) {
        pruneExpiredEntries();

        SessionState cachedState = statesBySessionId.get(sessionId);
        if (cachedState instanceof Closed closed && closed.isExpired(clock.instant())) {
            statesBySessionId.remove(sessionId, cachedState);
            loadLocksBySessionId.remove(sessionId);
            cachedState = null;
        }
        if (cachedState instanceof Open openState) {
            return Optional.of(openState.entry());
        }
        if (cachedState instanceof Closed) {
            return Optional.empty();
        }
        Object sessionLock = loadLocksBySessionId.computeIfAbsent(sessionId, ignoredSessionId -> new Object());
        synchronized (sessionLock) {
            SessionState recheckedState = statesBySessionId.get(sessionId);
            if (recheckedState instanceof Closed closed && closed.isExpired(clock.instant())) {
                statesBySessionId.remove(sessionId, recheckedState);
                recheckedState = null;
            }
            if (recheckedState instanceof Open openState) {
                return Optional.of(openState.entry());
            }
            if (recheckedState instanceof Closed) {
                return Optional.empty();
            }
            Optional<Entry> loadedEntry = loader.get();
            if (loadedEntry.isPresent()) {
                statesBySessionId.put(sessionId, new Open(loadedEntry.get()));
            } else {
                statesBySessionId.put(sessionId, new Closed(clock.instant().plus(CACHE_MISS_CLOSED_TTL)));
            }
            return loadedEntry;
        }
    }

    public void pruneExpiredEntries() {
        Instant now = clock.instant();
        for (Map.Entry<Long, SessionState> mapEntry : statesBySessionId.entrySet()) {
            if (mapEntry.getValue() instanceof Closed closed && closed.isExpired(now)) {
                statesBySessionId.remove(mapEntry.getKey(), mapEntry.getValue());
                loadLocksBySessionId.remove(mapEntry.getKey());
            }
        }
    }

    boolean hasSessionState(Long sessionId) {
        return statesBySessionId.containsKey(sessionId);
    }

    boolean hasPendingEntry(Long sessionId) {
        return pendingEntriesBySessionId.containsKey(sessionId);
    }

    boolean hasLoadLock(Long sessionId) {
        return loadLocksBySessionId.containsKey(sessionId);
    }
}
