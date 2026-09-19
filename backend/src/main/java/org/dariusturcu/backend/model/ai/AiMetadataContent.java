package org.dariusturcu.backend.model.ai;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiMetadataContent(
        String title,
        String artist,
        Integer releaseYear,
        String color,
        String confidence,
        String source,
        String reasoning,
        String verificationStatus
) {
}
