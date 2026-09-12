package org.dariusturcu.backend.model.session;

// position is the 0-based insertion index into the active player's timeline: 0 places
// before every existing card, timeline.size() places after every existing card, anything
// in between places the new card there.
public record PlaceCardRequest(int position) {
}
