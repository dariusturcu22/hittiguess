package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.session.PlayerCard;
import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundDTO;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.websocket.SessionBroadcastEvent;
import org.dariusturcu.backend.websocket.SessionEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlacementPreviewGateTest {

    private static final Long SESSION_ID = 501L;
    private static final Long ROUND_ID = 701L;
    private static final Long ACTIVE_PLAYER_USER_ID = 101L;
    private static final Long ACTIVE_PLAYER_ID = 201L;
    private static final int TIMELINE_SIZE = 4;
    private static final Instant INITIAL_TIME = Instant.parse("2026-01-01T12:00:00Z");

    private MutableClock clock;
    private PlacementPreviewGate gate;

    private static final class MutableClock extends Clock {
        private Instant currentInstant;
        private final ZoneId zone;

        MutableClock(Instant initialInstant) {
            this(initialInstant, ZoneOffset.UTC);
        }

        MutableClock(Instant initialInstant, ZoneId zone) {
            this.currentInstant = initialInstant;
            this.zone = zone;
        }

        void advance(Duration duration) {
            this.currentInstant = this.currentInstant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(INITIAL_TIME);
        gate = new PlacementPreviewGate(clock);
    }

    private Round buildMockRound() {
        Round mockRound = mock(Round.class);
        Player mockPlayer = mock(Player.class);
        User mockUser = mock(User.class);
        List<PlayerCard> mockCards = Collections.nCopies(TIMELINE_SIZE, mock(PlayerCard.class));

        when(mockRound.getId()).thenReturn(ROUND_ID);
        when(mockRound.getStatus()).thenReturn(RoundStatus.AWAITING_PLACEMENT);
        when(mockRound.getActivePlayer()).thenReturn(mockPlayer);
        when(mockPlayer.getId()).thenReturn(ACTIVE_PLAYER_ID);
        when(mockPlayer.getUser()).thenReturn(mockUser);
        when(mockPlayer.getTimeline()).thenReturn(mockCards);
        when(mockUser.getId()).thenReturn(ACTIVE_PLAYER_USER_ID);
        return mockRound;
    }

    @Test
    void sessionEnded_removesAllSessionStatePendingEntriesAndLocks() {
        gate.getOrLoad(SESSION_ID, Optional::empty);
        assertThat(gate.hasLoadLock(SESSION_ID)).isTrue();

        Round round = buildMockRound();
        gate.stageRound(SESSION_ID, round);
        assertThat(gate.hasPendingEntry(SESSION_ID)).isTrue();

        RoundDTO awaitingPlacementDTO = mock(RoundDTO.class);
        when(awaitingPlacementDTO.status()).thenReturn(RoundStatus.AWAITING_PLACEMENT);

        gate.onSessionBroadcastEvent(new SessionBroadcastEvent(
                SessionEventType.ROUND_STARTED, SESSION_ID, awaitingPlacementDTO));

        Optional<PlacementPreviewGate.Entry> loadedEntry = gate.getOrLoad(SESSION_ID, Optional::empty);
        assertThat(loadedEntry).isPresent();
        assertThat(gate.hasSessionState(SESSION_ID)).isTrue();
        assertThat(gate.hasLoadLock(SESSION_ID)).isTrue();

        // Stage another round so pending entries map has a key as well.
        gate.stageRound(SESSION_ID, round);
        assertThat(gate.hasPendingEntry(SESSION_ID)).isTrue();

        // When SESSION_ENDED arrives, all maps must be cleaned up.
        gate.onSessionBroadcastEvent(new SessionBroadcastEvent(
                SessionEventType.SESSION_ENDED, SESSION_ID, null));

        assertThat(gate.hasSessionState(SESSION_ID)).isFalse();
        assertThat(gate.hasPendingEntry(SESSION_ID)).isFalse();
        assertThat(gate.hasLoadLock(SESSION_ID)).isFalse();
    }

    @Test
    void cacheMiss_storesClosedStateThatExpiresAfterConfiguredDuration() {
        AtomicInteger loaderCalls = new AtomicInteger();
        PlacementPreviewGate.Entry expectedEntry = new PlacementPreviewGate.Entry(
                ROUND_ID, ACTIVE_PLAYER_USER_ID, ACTIVE_PLAYER_ID, TIMELINE_SIZE);

        // Initial fetch misses: sets Closed state with TTL.
        Optional<PlacementPreviewGate.Entry> initialMiss = gate.getOrLoad(SESSION_ID, () -> {
            loaderCalls.incrementAndGet();
            return Optional.empty();
        });
        assertThat(initialMiss).isEmpty();
        assertThat(loaderCalls.get()).isEqualTo(1);
        assertThat(gate.hasSessionState(SESSION_ID)).isTrue();

        // Query before TTL expires: returns empty without calling loader.
        Duration halfTtl = PlacementPreviewGate.CACHE_MISS_CLOSED_TTL.dividedBy(2);
        clock.advance(halfTtl);
        Optional<PlacementPreviewGate.Entry> intermediateResult = gate.getOrLoad(SESSION_ID, () -> {
            loaderCalls.incrementAndGet();
            return Optional.of(expectedEntry);
        });
        assertThat(intermediateResult).isEmpty();
        assertThat(loaderCalls.get()).isEqualTo(1);

        // Advance clock past TTL: Closed state expires and loader is called again.
        clock.advance(halfTtl.plusSeconds(5));
        Optional<PlacementPreviewGate.Entry> refreshedResult = gate.getOrLoad(SESSION_ID, () -> {
            loaderCalls.incrementAndGet();
            return Optional.of(expectedEntry);
        });
        assertThat(refreshedResult).contains(expectedEntry);
        assertThat(loaderCalls.get()).isEqualTo(2);
    }

    @Test
    void closedBetweenRounds_doesNotExpireOverTime() {
        RoundDTO bettingDTO = mock(RoundDTO.class);
        when(bettingDTO.status()).thenReturn(RoundStatus.BETTING);

        gate.onSessionBroadcastEvent(new SessionBroadcastEvent(
                SessionEventType.BETTING_OPENED, SESSION_ID, bettingDTO));

        assertThat(gate.hasSessionState(SESSION_ID)).isTrue();

        // Advance clock well beyond cache miss TTL.
        clock.advance(Duration.ofHours(2));

        AtomicInteger loaderCalls = new AtomicInteger();
        Optional<PlacementPreviewGate.Entry> result = gate.getOrLoad(SESSION_ID, () -> {
            loaderCalls.incrementAndGet();
            return Optional.of(new PlacementPreviewGate.Entry(
                    ROUND_ID, ACTIVE_PLAYER_USER_ID, ACTIVE_PLAYER_ID, TIMELINE_SIZE));
        });

        // Remains closed and loader is never called.
        assertThat(result).isEmpty();
        assertThat(loaderCalls.get()).isZero();
        assertThat(gate.hasSessionState(SESSION_ID)).isTrue();
    }

    @Test
    void pruneExpiredEntries_cleansUpExpiredCacheMisses() {
        gate.getOrLoad(SESSION_ID, Optional::empty);
        assertThat(gate.hasSessionState(SESSION_ID)).isTrue();
        assertThat(gate.hasLoadLock(SESSION_ID)).isTrue();

        clock.advance(PlacementPreviewGate.CACHE_MISS_CLOSED_TTL.plusSeconds(10));
        gate.pruneExpiredEntries();

        assertThat(gate.hasSessionState(SESSION_ID)).isFalse();
        assertThat(gate.hasLoadLock(SESSION_ID)).isFalse();
    }
}
