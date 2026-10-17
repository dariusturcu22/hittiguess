package org.dariusturcu.backend.model.song;

import java.util.Set;

public record EnqueueResultDTO(
        Set<String> enqueuedYoutubeIds,
        Set<String> skippedAlreadyKnownYoutubeIds,
        Set<String> skippedAlreadyQueuedYoutubeIds,
        Long batchId) {
    public EnqueueResultDTO(Set<String> enqueuedYoutubeIds, Set<String> skippedAlreadyKnownYoutubeIds,
            Set<String> skippedAlreadyQueuedYoutubeIds) {
        this(enqueuedYoutubeIds, skippedAlreadyKnownYoutubeIds, skippedAlreadyQueuedYoutubeIds, null);
    }
}
