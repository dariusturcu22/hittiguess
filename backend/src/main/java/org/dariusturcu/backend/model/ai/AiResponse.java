package org.dariusturcu.backend.model.ai;

import java.time.LocalDateTime;

public record AiResponse(
        SongMetadataResponse content,
        String model,
        long durationMs,
        LocalDateTime timestamp,
        String status,
        String rejectionReason,
        String rejectionDetail
) {
}
