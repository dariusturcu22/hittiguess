package org.dariusturcu.backend.model.admin;

import java.util.List;

/**
 * @param estimatedDrainSeconds the slowest stage's estimate, since stages work in parallel; null when any
 *                              stage that still holds jobs has an unknown estimate.
 */
public record QueueOverviewDTO(List<QueueStageDTO> stages, boolean stagesEnabled,
        long youtubeUnitsRemaining, long youtubeReserveUnits, boolean youtubeBackfillLimited,
        Long estimatedDrainSeconds) {
}
