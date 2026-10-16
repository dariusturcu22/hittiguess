package org.dariusturcu.backend.pipeline;

// One queue of the metadata pipeline, in the order a run moves through them.
public enum PipelineStage {
    YOUTUBE_METADATA,
    MUSICBRAINZ,
    DISCOGS,
    WIKIDATA,
    WIKIPEDIA,
    LLM_RECONCILIATION,
    EMBEDDING
}
