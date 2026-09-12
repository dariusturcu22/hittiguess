package org.dariusturcu.backend.model.session;

// artist/title/year are populated only once status is REVEALED or SCORED: the reveal is
// what makes them public, broadcasting the DTO earlier in the round's life omits them.
public record RoundDTO(
        Long id,
        int roundNumber,
        Long activePlayerId,
        Long djPlayerId,
        RoundStatus status,
        Integer placedPosition,
        Boolean placementCorrect,
        Long bettorPlayerId,
        String revealedArtist,
        String revealedTitle,
        Integer revealedYear) {
}
