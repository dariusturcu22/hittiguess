package org.dariusturcu.backend.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// A compact per-game summary, not the full round-by-round transcript: the transactional
// GameSession/Round/Guess rows already carry that detail and purge on story 10's schedule.
// This is what story 34's game history feature reads from instead.
public record GameSessionEndedPayload(
        long groupId,
        List<Long> playerUserIds,
        Long winningPlayerUserId,
        Map<Long, Integer> cardsWonByPlayerUserId,
        Map<Long, Integer> finalScoreByPlayerUserId,
        Instant endedAt
) {
}
