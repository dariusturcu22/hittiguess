package org.dariusturcu.backend.model.user;

import java.time.Instant;

public record ChatMessageExportDTO(
        Long id,
        Long groupId,
        String content,
        Instant createdAt
) {
}
