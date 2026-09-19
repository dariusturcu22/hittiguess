package org.dariusturcu.backend.model.session;

public record LeaderboardEntryDTO(Long playerId, String displayName, int value) {
}
