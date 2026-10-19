package org.dariusturcu.backend.model.session;

// Relayed to every player so spectators can watch the active player's placement live.
// Never persisted: it only mirrors the active player's screen until lock-in.
public record PlacementPreviewDTO(Long roundId, Long activePlayerId, Integer position) {
}
