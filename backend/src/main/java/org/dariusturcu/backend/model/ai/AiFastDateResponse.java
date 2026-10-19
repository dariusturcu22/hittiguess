package org.dariusturcu.backend.model.ai;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

// A provisional year from one fast-tier lane, or no year when neither lane found one.
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiFastDateResponse(Integer releaseYear, String confidence, String source, String lane) {
}
