package org.dariusturcu.backend.model.admin;

import java.util.List;

public record QueueOverviewDTO(List<QueueStageDTO> stages, boolean stagesEnabled,
        long youtubeUnitsRemaining, long youtubeReserveUnits, boolean youtubeBackfillLimited) {
}
