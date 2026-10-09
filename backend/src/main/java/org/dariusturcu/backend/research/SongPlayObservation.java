package org.dariusturcu.backend.research;

import java.time.Instant;
import java.util.UUID;

public record SongPlayObservation(UUID eventId, Instant occurredAt, long songId,
        UUID researchPlayerId, UUID gameCorrelationId, String placementOutcome,
        int timelineCardCount, int validInsertionSlotCount, boolean titleAttempted,
        boolean titleCorrect, int artistAttempts, int correctArtists,
        String requestedDifficultyTier, String rulesVersion) {}
