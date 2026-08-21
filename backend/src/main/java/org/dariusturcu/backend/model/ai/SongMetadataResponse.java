package org.dariusturcu.backend.model.ai;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SongMetadataResponse(
        String title,
        String artist,
        @JsonDeserialize(using = FlexibleYearDeserializer.class)
        Integer releaseYear,
        String gradientColor1,
        String gradientColor2
) {

}
