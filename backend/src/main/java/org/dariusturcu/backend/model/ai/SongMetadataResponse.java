package org.dariusturcu.backend.model.ai;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SongMetadataResponse(
        String title,
        String artist,
        Integer releaseYear,
        String gradientColor1,
        String gradientColor2,
        String confidence,
        String source,
        String reasoning
) {

}
