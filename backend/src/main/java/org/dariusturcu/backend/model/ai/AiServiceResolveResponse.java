package org.dariusturcu.backend.model.ai;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiServiceResolveResponse(
        String status,
        String model,
        SongMetadataResponse content,
        String rejectionReason,
        String rejectionDetail
) {
}
