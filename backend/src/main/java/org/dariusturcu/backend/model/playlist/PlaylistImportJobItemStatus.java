package org.dariusturcu.backend.model.playlist;

// PENDING items are waiting for a free slot; IDENTIFYING and DATING are the two
// fast-tier stages a song is actively being worked through.
public enum PlaylistImportJobItemStatus {
    PENDING,
    IDENTIFYING,
    DATING,
    ALREADY_KNOWN,
    RESOLVED,
    UNRESOLVED
}
