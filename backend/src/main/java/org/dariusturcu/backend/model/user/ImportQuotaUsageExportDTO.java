package org.dariusturcu.backend.model.user;

import java.time.LocalDate;

public record ImportQuotaUsageExportDTO(
        LocalDate usageDate,
        int resolutionCount
) {
}
