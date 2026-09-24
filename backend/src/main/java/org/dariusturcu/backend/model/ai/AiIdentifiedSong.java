package org.dariusturcu.backend.model.ai;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiIdentifiedSong(
        String title,
        List<String> mainArtists,
        List<String> featuredArtists,
        String color) {
}
