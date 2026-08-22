package org.dariusturcu.backend.model.ai;

public record AiServiceResolveResponse(String status, String model, SongMetadataResponse content) {
}
