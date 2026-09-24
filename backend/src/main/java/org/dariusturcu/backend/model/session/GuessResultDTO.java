package org.dariusturcu.backend.model.session;

// Sent only to the player who guessed: whether this submission's artist and title were
// right, and where their guessing for the round stands afterwards.
public record GuessResultDTO(Long roundId, boolean artistCorrect, boolean titleCorrect, GuessStateDTO state) {
}
