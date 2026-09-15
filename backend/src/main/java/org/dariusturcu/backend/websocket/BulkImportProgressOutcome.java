package org.dariusturcu.backend.websocket;

// The outcome BulkImportService reports for a single video id as its on-the-spot
// import loop processes it, matching the per-song row states
// docs/design/source/ImportPlaylistProcessingDark.dc.html renders.
public enum BulkImportProgressOutcome {
    ALREADY_KNOWN,
    RESOLVED,
    UNRESOLVED
}
