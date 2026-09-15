package org.dariusturcu.backend.model.song;

import java.time.Instant;

public record ReportSummaryDTO(
        Long reportId,
        Long reporterId,
        String message,
        Integer suggestedCorrectYear,
        String sources,
        Instant createdAt) {
}
