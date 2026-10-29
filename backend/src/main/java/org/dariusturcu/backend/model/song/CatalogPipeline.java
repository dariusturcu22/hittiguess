package org.dariusturcu.backend.model.song;

// Which metadata pipeline last decided a catalog row: the fast tier with its patient
// recheck still outstanding, the full patient pipeline, no backlog evidence either way,
// or a row that predates pipeline tracking entirely.
public enum CatalogPipeline {
    FAST,
    FULL,
    UNKNOWN,
    LEGACY
}
