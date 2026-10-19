package org.dariusturcu.backend.model.session;

// One player's artist and title guessing for one round. Artists are guessed one at a
// time: each correct one counts toward correctArtistCount, and a wrong one closes
// artist guessing for the round. The title gets exactly one attempt.
public record GuessStateDTO(
        Long roundId,
        int artistCount,
        int correctArtistCount,
        boolean artistGuessingClosed,
        boolean titleGuessed,
        boolean titleCorrect,
        boolean tokenEarned) {
}
