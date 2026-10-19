package org.dariusturcu.backend.history;

public record GameParticipantSummary(long id, Long userId, String displayName,
        String participationStatus, int finalCardCount, int cardRank, int artistRank,
        int titleRank, boolean isWinner, int placementAttempts, int correctPlacements,
        int titleAttempts, int correctTitles, int artistAttempts, int correctArtists,
        int betsPlaced, int betsWon) {}
