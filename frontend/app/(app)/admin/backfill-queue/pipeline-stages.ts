export const STAGES = [
  {
    id: "YOUTUBE_METADATA",
    slug: "youtube-metadata",
    label: "YouTube",
    short: "YT",
  },
  { id: "MUSICBRAINZ", slug: "musicbrainz", label: "MusicBrainz", short: "MB" },
  { id: "DISCOGS", slug: "discogs", label: "Discogs", short: "DC" },
  { id: "WIKIDATA", slug: "wikidata", label: "Wikidata", short: "WD" },
  { id: "WIKIPEDIA", slug: "wikipedia", label: "Wikipedia", short: "WP" },
  {
    id: "LLM_RECONCILIATION",
    slug: "llm-reconciliation",
    label: "LLM reconcile",
    short: "LLM",
  },
  { id: "EMBEDDING", slug: "embedding", label: "Embedding", short: "EMB" },
] as const;
