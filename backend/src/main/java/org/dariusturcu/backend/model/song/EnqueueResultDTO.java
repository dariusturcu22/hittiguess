package org.dariusturcu.backend.model.song;

import java.util.Set;

/**
 * The outcome of an admin bulk-enqueue: which submitted IDs were added to the
 * backlog, which were skipped because the catalog already knows the song, and
 * which were skipped because they were already sitting in the backlog.
 */
public record EnqueueResultDTO(
        Set<String> enqueuedYoutubeIds,
        Set<String> skippedAlreadyKnownYoutubeIds,
        Set<String> skippedAlreadyQueuedYoutubeIds) {
}
