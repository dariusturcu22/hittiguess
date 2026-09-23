package org.dariusturcu.backend.model.ai;

// Deliberately no @JsonNaming here: this record is the outbound-facing shape
// returned to the frontend (which expects camelCase, matching every other
// DTO in the API), unlike AiMetadataContent, which mirrors the AI
// microservice's snake_case wire format on the way in.
import java.util.List;

public record SongMetadataResponse(
        String title,
        List<String> mainArtists,
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
