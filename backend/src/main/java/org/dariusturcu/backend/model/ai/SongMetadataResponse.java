package org.dariusturcu.backend.model.ai;

// Deliberately no @JsonNaming here: this record is the outbound-facing shape
// returned to the frontend (which expects camelCase, matching every other
// DTO in the API), unlike AiMetadataContent, which mirrors the AI
// microservice's snake_case wire format on the way in.
public record SongMetadataResponse(
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
