package org.dariusturcu.backend.history;

import java.time.Instant;
import java.util.List;

public record GameSummary(long id, String groupName, Instant startedAt, Instant endedAt,
        String mode, String difficultyTier, int winTargetCards, int participantCount,
        int turnsPlayed, String endingReason, String rulesVersion,
        List<GameParticipantSummary> participants) {}
