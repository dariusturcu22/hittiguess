package org.dariusturcu.backend.model.ai;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

// The fast tier's first pass for one video. A SUCCESS carries either the identified
// song, which still needs a year, or a verified duplicate's complete answer.
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiIdentifyResponse(
        String status,
        String model,
        AiIdentifiedSong identified,
        AiMetadataContent duplicate,
        String rejectionReason,
        String rejectionDetail) {
}
