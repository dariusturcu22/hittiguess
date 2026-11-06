package org.dariusturcu.backend.model.user;

import org.dariusturcu.backend.model.song.SongReportStatus;

import java.time.Instant;

public record SongReportExportDTO(
        Long id,
        Long songId,
        String message,
        Integer suggestedCorrectYear,
        String sources,
        SongReportStatus status,
        Instant createdAt
) {
}
