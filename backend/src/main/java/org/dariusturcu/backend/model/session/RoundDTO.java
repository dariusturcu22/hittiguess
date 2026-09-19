package org.dariusturcu.backend.model.session;

import java.util.List;

// artist/title/year are populated only once status is REVEALED or SCORED: the reveal is
// what makes them public, broadcasting the DTO earlier in the round's life omits them.
// bets lists every bet accepted so far this round against the active player's timeline,
// one entry per distinct gap.
public record RoundDTO(
        Long id,
        int roundNumber,
        Long activePlayerId,
        Long djPlayerId,
        RoundStatus status,
        Integer placedPosition,
        Boolean placementCorrect,
        List<BetDTO> bets,
        String revealedArtist,
        String revealedTitle,
        Integer revealedYear) {
}
