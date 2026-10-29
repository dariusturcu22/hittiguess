package org.dariusturcu.backend.model.song;

// Where a backlog row came from: an admin seeding the catalog, an admin rerunning a song
// that is already in the catalog, or a provisional answer a user path already saved, queued
// for the patient tier to recheck. An admin rerun says nothing about who added the song.
public enum PendingImportOrigin {
    ADMIN_SEED,
    ADMIN_RECHECK,
    FAST_TIER_RECHECK,
    USER_ADD_RECHECK
}
