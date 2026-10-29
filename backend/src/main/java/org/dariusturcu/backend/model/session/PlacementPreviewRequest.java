package org.dariusturcu.backend.model.session;

// The active player's card as it moves over the timeline: the hovered gap, its center
// as fractions of the timeline box, and which phase it is in. Null position means no
// gap is hovered. Coordinates are clamped by the service before relaying.
public record PlacementPreviewRequest(
        Integer position,
        Double x,
        Double y,
        PlacementPreviewPhase phase
) {
}
