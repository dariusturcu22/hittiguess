package org.dariusturcu.backend.model.session;

// What the active player's card is doing, so every client can render the same card
// floating, following the drag, or resting in the chosen gap.
public enum PlacementPreviewPhase {
    IDLE,
    DRAGGING,
    DROPPED
}
