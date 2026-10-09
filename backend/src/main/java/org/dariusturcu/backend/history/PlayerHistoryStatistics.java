package org.dariusturcu.backend.history;

public record PlayerHistoryStatistics(long gamesPlayed, long wins, long interruptedGames,
        long placementAttempts, long correctPlacements, long correctTitles, long correctArtists) {}
