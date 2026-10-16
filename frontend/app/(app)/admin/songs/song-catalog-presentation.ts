export type CatalogStatus = "VERIFIED" | "NEEDS_REVIEW" | "IN_QUEUE" | "PROCESSING" | "ERROR";
export type CatalogPipeline = "FAST" | "FULL" | "UNKNOWN";
export type CatalogAddedByKind = "USER" | "ADMIN_SEED" | "UNKNOWN";
export type QueueOrigin = "ADMIN_SEED" | "FAST_TIER_RECHECK" | "USER_ADD_RECHECK";
export type QueueStatus = "PENDING" | "PROCESSING" | "DONE" | "FAILED";

export const MINIMUM_SEARCH_LENGTH = 3;
export const SEARCH_DEBOUNCE_MILLISECONDS = 400;
export const CATALOG_PAGE_SIZE = 25;
export const FIRST_PAGE = 0;

export const SEARCH_HINT =
  "Search runs after 3 characters and a short pause. Results come 25 at a time.";

export type CatalogColumnKey = "title" | "artist" | "year" | "status" | "pipeline" | "added" | "addedBy";

// Hidden columns remain available in the song drawer.
export type CatalogColumnPriority = "essential" | "medium" | "low";

export interface CatalogColumn {
  key: CatalogColumnKey;
  label: string;
  priority: CatalogColumnPriority;
  sizeClassName: string;
}

const COLUMN_VISIBILITY_CLASS: Record<CatalogColumnPriority, string> = {
  essential: "",
  medium: "hidden @4xl:block",
  low: "hidden @6xl:block",
};


export const CATALOG_COLUMNS: CatalogColumn[] = [
  { key: "title", label: "Title", priority: "essential", sizeClassName: "min-w-0 flex-[3] @6xl:w-[270px] @6xl:flex-none" },
  { key: "artist", label: "Artist", priority: "essential", sizeClassName: "min-w-0 flex-[2] @6xl:w-[210px] @6xl:flex-none" },
  { key: "year", label: "Year", priority: "essential", sizeClassName: "w-[70px] shrink-0 @max-4xl:w-[56px]" },
  { key: "status", label: "Status", priority: "essential", sizeClassName: "w-[130px] shrink-0" },
  { key: "pipeline", label: "Pipeline", priority: "medium", sizeClassName: "w-[90px] shrink-0" },
  { key: "added", label: "Added", priority: "essential", sizeClassName: "w-[120px] shrink-0 @max-4xl:w-[96px]" },
  { key: "addedBy", label: "Added by", priority: "low", sizeClassName: "min-w-0 flex-1" },
];

export function catalogColumnClassName(key: CatalogColumnKey) {
  const column = CATALOG_COLUMNS.find((candidate) => candidate.key === key);
  if (!column) {
    return "";
  }
  return `${column.sizeClassName} ${COLUMN_VISIBILITY_CLASS[column.priority]}`.trim();
}

export interface StatusPresentation {
  label: string;
  pillClassName: string;
}

// Pill colors follow the AdminSongCatalog mockups: the light theme's needs-review
// pill uses the peach token and the dark theme's uses the warning token, so that
// pill switches token per theme.
export const STATUS_PRESENTATION: Record<CatalogStatus, StatusPresentation> = {
  VERIFIED: { label: "VERIFIED", pillClassName: "bg-green/15 text-green dark:bg-green/18" },
  NEEDS_REVIEW: {
    label: "NEEDS REVIEW",
    pillClassName: "bg-peach/16 text-peach dark:bg-warning/18 dark:text-warning",
  },
  IN_QUEUE: {
    label: "IN QUEUE",
    pillClassName: "bg-border-strong/12 text-muted-foreground dark:bg-secondary/60",
  },
  PROCESSING: { label: "PROCESSING", pillClassName: "bg-blue/15 text-blue dark:bg-blue/18" },
  ERROR: { label: "ERROR", pillClassName: "bg-destructive/13 text-destructive dark:bg-destructive/18" },
};

export interface StatusFilter {
  label: string;
  status: CatalogStatus | null;
  countKey: "all" | "verified" | "needsReview" | "inQueue" | "processing" | "error";
}

export const STATUS_FILTERS: StatusFilter[] = [
  { label: "All", status: null, countKey: "all" },
  { label: "Verified", status: "VERIFIED", countKey: "verified" },
  { label: "Needs review", status: "NEEDS_REVIEW", countKey: "needsReview" },
  { label: "In queue", status: "IN_QUEUE", countKey: "inQueue" },
  { label: "Processing", status: "PROCESSING", countKey: "processing" },
  { label: "Error", status: "ERROR", countKey: "error" },
];

const PIPELINE_LABELS: Record<CatalogPipeline, string> = {
  FAST: "Fast",
  FULL: "Full",
  UNKNOWN: "Unknown",
};

const ADMIN_SEED_LABEL = "Admin seed";
const UNKNOWN_LABEL = "Unknown";

const QUEUE_ORIGIN_LABELS: Record<QueueOrigin, string> = {
  ADMIN_SEED: "Admin seed",
  FAST_TIER_RECHECK: "Fast-tier import recheck",
  USER_ADD_RECHECK: "Added by hand, recheck",
};

const QUEUE_STATUS_LABELS: Record<QueueStatus, string> = {
  PENDING: "waiting",
  PROCESSING: "processing",
  DONE: "done",
  FAILED: "failed",
};

const NUMBER_FORMAT = new Intl.NumberFormat("en-US");
const DATE_FORMAT = new Intl.DateTimeFormat("en-US", {
  month: "short",
  day: "numeric",
  year: "numeric",
});
const DATE_TIME_FORMAT = new Intl.DateTimeFormat("en-US", {
  month: "short",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

export function presentStatus(status: CatalogStatus | undefined): StatusPresentation {
  return status ? STATUS_PRESENTATION[status] : STATUS_PRESENTATION.NEEDS_REVIEW;
}

export function pipelineLabel(pipeline: CatalogPipeline | undefined): string {
  return pipeline ? PIPELINE_LABELS[pipeline] : PIPELINE_LABELS.UNKNOWN;
}

export function addedByLabel(addedBy: string | undefined | null, addedByKind: CatalogAddedByKind | undefined): string {
  if (addedByKind === "USER" && addedBy) {
    return addedBy;
  }
  return addedByKind === "ADMIN_SEED" ? ADMIN_SEED_LABEL : UNKNOWN_LABEL;
}

export function formatCount(count: number | undefined): string {
  return NUMBER_FORMAT.format(count ?? 0);
}

export function formatCatalogDate(isoTimestamp: string | undefined | null): string {
  return isoTimestamp ? DATE_FORMAT.format(new Date(isoTimestamp)) : UNKNOWN_LABEL;
}

export function formatHistoryTime(isoTimestamp: string | undefined | null): string {
  return isoTimestamp ? DATE_TIME_FORMAT.format(new Date(isoTimestamp)) : "";
}

export function queueOriginLabel(origin: QueueOrigin | undefined): string {
  return origin ? QUEUE_ORIGIN_LABELS[origin] : UNKNOWN_LABEL;
}

export function queueStatusLabel(status: QueueStatus | undefined): string {
  return status ? QUEUE_STATUS_LABELS[status] : UNKNOWN_LABEL.toLowerCase();
}

// Search applies only once the trimmed text reaches the minimum; shorter text
// keeps the unfiltered list.
export function effectiveSearchText(typedText: string): string | undefined {
  const trimmedText = typedText.trim();
  return trimmedText.length >= MINIMUM_SEARCH_LENGTH ? trimmedText : undefined;
}

export function showingRangeText(page: number, itemCount: number, totalElements: number): string {
  if (totalElements === 0 || itemCount === 0) {
    return `Showing 0 of ${formatCount(totalElements)} songs`;
  }
  const firstShown = page * CATALOG_PAGE_SIZE + 1;
  const lastShown = page * CATALOG_PAGE_SIZE + itemCount;
  return `Showing ${formatCount(firstShown)} to ${formatCount(lastShown)} of ${formatCount(totalElements)} songs`;
}

export function detailMetaLine(detail: {
  releaseYear?: number | null;
  pipeline?: CatalogPipeline;
  createdAt?: string | null;
  addedBy?: string | null;
  addedByKind?: CatalogAddedByKind;
}): string {
  const yearPart = detail.releaseYear ? `Year ${detail.releaseYear}` : "Year pending";
  const pipelinePart = `${pipelineLabel(detail.pipeline).toLowerCase()} pipeline`;
  const addedPart = detail.createdAt
    ? `added ${formatCatalogDate(detail.createdAt)}`
    : "added on an unknown date";
  let adderPart = "";
  if (detail.addedByKind === "USER" && detail.addedBy) {
    adderPart = ` by ${detail.addedBy}`;
  } else if (detail.addedByKind === "ADMIN_SEED") {
    adderPart = " by admin seed";
  }
  return `${yearPart}, ${pipelinePart}, ${addedPart}${adderPart}`;
}
