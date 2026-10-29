package org.dariusturcu.backend.model.song;

// Who put a catalog row there: a user account, an admin catalog seed with no account
// attached, a row that predates pipeline tracking, or nothing on record.
public enum CatalogAddedByKind {
    USER,
    ADMIN_SEED,
    LEGACY,
    UNKNOWN
}
