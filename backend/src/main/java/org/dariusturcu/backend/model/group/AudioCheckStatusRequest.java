package org.dariusturcu.backend.model.group;

import jakarta.validation.constraints.NotNull;

// A player's own share status, reported by their browser during the audio check or while
// a game runs. CANNOT_SHARE is refused: the browser capability report decides it.
public record AudioCheckStatusRequest(@NotNull AudioCheckStatus status) {
}
