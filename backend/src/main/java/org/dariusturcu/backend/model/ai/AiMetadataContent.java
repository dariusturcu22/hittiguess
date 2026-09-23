package org.dariusturcu.backend.model.ai;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiMetadataContent(
        String title,
        String artist,
        List<String> featuredArtists,
        Integer releaseYear,
        String color,
        String confidence,
        String source,
        String reasoning,
        String verificationStatus,
        Integer sitelinksCount
) {
}
