package org.dariusturcu.backend.model.playlist;

import java.time.Instant;
import java.util.List;

public record PlaylistImportJobDTO(
        String id,
        Long playlistId,
        PlaylistImportJobStatus status,
        Instant createdAt,
        Instant completedAt,
        List<PlaylistImportJobItemDTO> items
) {
}
