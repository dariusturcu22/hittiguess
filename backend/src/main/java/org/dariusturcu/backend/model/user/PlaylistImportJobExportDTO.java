package org.dariusturcu.backend.model.user;

import org.dariusturcu.backend.model.playlist.PlaylistImportJobStatus;

import java.time.Instant;

public record PlaylistImportJobExportDTO(
        String id,
        Long playlistId,
        PlaylistImportJobStatus status,
        Instant createdAt,
        Instant completedAt
) {
}
