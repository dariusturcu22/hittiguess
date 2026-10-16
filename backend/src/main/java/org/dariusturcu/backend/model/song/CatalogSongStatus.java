package org.dariusturcu.backend.model.song;

// Where a catalog row stands for an admin: in the backlog now, or resolved with its
// verification outcome. A song with an active backlog row shows the backlog state.
public enum CatalogSongStatus {
    VERIFIED,
    NEEDS_REVIEW,
    IN_QUEUE,
    PROCESSING,
    ERROR
}
