package org.dariusturcu.backend.model.song;

// Who put a catalog row there: a user account, an admin catalog seed with no account
// attached, or nothing on record.
public enum CatalogAddedByKind {
    USER,
    ADMIN_SEED,
    UNKNOWN
}
