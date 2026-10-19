package org.dariusturcu.backend.model.session;

// rank is a competition rank: players with the same value share it, and the next
// distinct value skips the places they took.
public record LeaderboardEntryDTO(Long playerId, String displayName, int value, int rank) {
}
