package org.dariusturcu.backend.model.session;

// Sent only to the player who guessed: whether their artist and title were right.
public record GuessResultDTO(Long roundId, boolean artistCorrect, boolean titleCorrect) {
}
