package org.dariusturcu.backend.model.song;

// Where a backlog row came from: an admin seeding the catalog, or a provisional answer a
// user path already saved, queued for the patient tier to recheck.
public enum PendingImportOrigin {
    ADMIN_SEED,
    FAST_TIER_RECHECK,
    USER_ADD_RECHECK
}
