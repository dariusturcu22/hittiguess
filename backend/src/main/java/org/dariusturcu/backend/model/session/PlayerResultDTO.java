package org.dariusturcu.backend.model.session;

public record PlayerResultDTO(Long playerId, String displayName, int cardCount, int rank) {
}
