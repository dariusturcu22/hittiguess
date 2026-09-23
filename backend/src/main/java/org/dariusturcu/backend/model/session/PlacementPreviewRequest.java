package org.dariusturcu.backend.model.session;

// The gap the active player's card is currently hovering over or resting in before
// lock-in, as the same 0-based insertion index PlaceCardRequest uses. Null clears it.
public record PlacementPreviewRequest(Integer position) {
}
