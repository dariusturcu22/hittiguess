package org.dariusturcu.backend.model.session;

// One accepted bet against the round's active-player timeline: which player placed it and
// which gap they chose.
public record BetDTO(Long playerId, int position) {
}
