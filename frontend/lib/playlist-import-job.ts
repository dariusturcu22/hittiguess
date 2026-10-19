"use client";

export interface ActivePlaylistImportJob {
  importJobId: string;
  playlistId: number;
}

const ACTIVE_IMPORT_STORAGE_KEY = "hittiguess-active-playlist-import";
export const PLAYLIST_IMPORT_STARTED_EVENT_NAME = "playlist-import-started";

// The sidebar indicator and the detail view both need the running import after a
// reload or a navigation, so the youtube page persists it here on start and the
// readers clear it once the job is gone.
export function loadActiveImportJob(): ActivePlaylistImportJob | null {
  if (typeof window === "undefined") {
    return null;
  }
  const storedJob = window.localStorage.getItem(ACTIVE_IMPORT_STORAGE_KEY);
  if (!storedJob) {
    return null;
  }
  try {
    return JSON.parse(storedJob) as ActivePlaylistImportJob;
  } catch {
    window.localStorage.removeItem(ACTIVE_IMPORT_STORAGE_KEY);
    return null;
  }
}

export function saveActiveImportJob(job: ActivePlaylistImportJob) {
  window.localStorage.setItem(ACTIVE_IMPORT_STORAGE_KEY, JSON.stringify(job));
  window.dispatchEvent(new CustomEvent(PLAYLIST_IMPORT_STARTED_EVENT_NAME, { detail: job }));
}

export function clearActiveImportJob() {
  window.localStorage.removeItem(ACTIVE_IMPORT_STORAGE_KEY);
}

const SETTLED_ITEM_STATUSES = new Set(["RESOLVED", "ALREADY_KNOWN", "UNRESOLVED"]);
const WORKING_ITEM_STATUSES = new Set(["IDENTIFYING", "DATING"]);
const ADDED_ITEM_STATUSES = new Set(["RESOLVED", "ALREADY_KNOWN"]);

export interface ImportItemCounts {
  total: number;
  settled: number;
  working: number;
  waiting: number;
  added: number;
  unmatched: number;
}

// Where an import's songs stand: waiting for a free slot, being worked on (identified,
// then dated), or settled as added or unmatched.
export function countImportItems(items: { status?: string }[]): ImportItemCounts {
  const statusOf = (item: { status?: string }) => item.status ?? "PENDING";
  return {
    total: items.length,
    settled: items.filter((item) => SETTLED_ITEM_STATUSES.has(statusOf(item))).length,
    working: items.filter((item) => WORKING_ITEM_STATUSES.has(statusOf(item))).length,
    waiting: items.filter((item) => statusOf(item) === "PENDING").length,
    added: items.filter((item) => ADDED_ITEM_STATUSES.has(statusOf(item))).length,
    unmatched: items.filter((item) => statusOf(item) === "UNRESOLVED").length,
  };
}
