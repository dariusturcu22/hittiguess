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
