package org.dariusturcu.backend.model.song;

// Which metadata pipeline last decided a catalog row: the fast tier with its patient
// recheck still outstanding, the full patient pipeline, or no backlog evidence either way.
public enum CatalogPipeline {
    FAST,
    FULL,
    UNKNOWN
}
