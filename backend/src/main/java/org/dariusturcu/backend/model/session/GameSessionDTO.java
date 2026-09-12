package org.dariusturcu.backend.model.session;

import org.dariusturcu.backend.model.group.DjMode;

import java.util.List;

public record GameSessionDTO(
        Long id,
        Long groupId,
        SessionStatus status,
        DjMode djMode,
        int winConditionCardCount,
        int currentRoundNumber,
        List<PlayerDTO> players,
        RoundDTO currentRound) {
}
