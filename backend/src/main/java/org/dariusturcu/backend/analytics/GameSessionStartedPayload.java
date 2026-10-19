package org.dariusturcu.backend.analytics;

import java.time.Instant;
import java.util.List;

public record GameSessionStartedPayload(
        long groupId,
        List<Long> playerUserIds,
        Instant startedAt
) {
}
