package org.dariusturcu.backend.model.usage;

import java.time.Instant;
import java.time.LocalDate;

/**
 * YouTube Data API quota for the current quota day, which follows Pacific time.
 */
public record YoutubeQuotaUsageDTO(
        long unitsUsedToday,
        long dailyLimit,
        long unitsRemaining,
        LocalDate quotaDay,
        Instant resetsAt) {
}
