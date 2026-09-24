package org.dariusturcu.backend.model.song;

import java.util.List;

/**
 * The admin backlog view: how many imports are still pending, how many were
 * processed today, how much of today's drain quota remains, and the newest
 * rechecks of provisional answers with their provisional and patient years.
 */
public record BacklogStatusDTO(
        long pendingCount,
        long processedTodayCount,
        long dailyDrainQuota,
        long quotaRemainingToday,
        List<BacklogQueueItemDTO> queueItems,
        List<PatientRecheckDTO> recentRechecks,
        boolean draining) {
}
