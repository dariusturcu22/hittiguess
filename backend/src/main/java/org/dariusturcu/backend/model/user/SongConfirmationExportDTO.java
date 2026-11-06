package org.dariusturcu.backend.model.user;

import java.time.Instant;

public record SongConfirmationExportDTO(
        Long id,
        Long songId,
        Instant createdAt
) {
}
