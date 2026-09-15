package org.dariusturcu.backend.model.song;

/**
 * The admin backlog view: how many imports are still pending, how many were
 * processed today, and how much of today's drain quota remains.
 */
public record BacklogStatusDTO(
        long pendingCount,
        long processedTodayCount,
        long dailyDrainQuota,
        long quotaRemainingToday) {
}
