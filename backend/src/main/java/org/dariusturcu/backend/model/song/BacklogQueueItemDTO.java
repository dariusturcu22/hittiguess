package org.dariusturcu.backend.model.song;

public record BacklogQueueItemDTO(
        String youtubeId,
        PendingImportStatus status,
        String failureReason) {
}
