import type { RecheckCandidateDTO } from "@/hooks/models/recheckCandidateDTO";

export type SelectionState = "none" | "some" | "all";

export interface MonthGroup {
  key: string;
  label: string;
  songs: RecheckCandidateDTO[];
}

const UNKNOWN_MONTH_KEY = "unknown";
const UNKNOWN_MONTH_LABEL = "Date unknown";
const MONTH_KEY_LENGTH = 7;
const MONTH_LABEL_LOCALE = "en-US";

export const GROUPING_NOTE =
  "Grouped by month last checked. Songs never checked are grouped by the month they were added.";

function monthKey(lastCheckedAt: string | undefined): string {
  if (!lastCheckedAt) return UNKNOWN_MONTH_KEY;
  const parsed = new Date(lastCheckedAt);
  if (Number.isNaN(parsed.getTime())) return UNKNOWN_MONTH_KEY;
  return parsed.toISOString().slice(0, MONTH_KEY_LENGTH);
}

function monthLabel(key: string): string {
  if (key === UNKNOWN_MONTH_KEY) return UNKNOWN_MONTH_LABEL;
  return new Date(`${key}-01T00:00:00Z`).toLocaleString(MONTH_LABEL_LOCALE, {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

export function groupByMonth(candidates: RecheckCandidateDTO[]): MonthGroup[] {
  const groups = new Map<string, RecheckCandidateDTO[]>();
  for (const candidate of candidates) {
    const key = monthKey(candidate.lastCheckedAt);
    const members = groups.get(key);
    if (members) members.push(candidate);
    else groups.set(key, [candidate]);
  }
  return [...groups.entries()]
    .sort(([firstKey], [secondKey]) => {
      if (firstKey === UNKNOWN_MONTH_KEY) return 1;
      if (secondKey === UNKNOWN_MONTH_KEY) return -1;
      return secondKey.localeCompare(firstKey);
    })
    .map(([key, songs]) => ({ key, label: monthLabel(key), songs }));
}

export function selectableSongIds(songs: RecheckCandidateDTO[]): number[] {
  return songs.filter((song) => !song.inActiveGame).map((song) => song.songId);
}

export function selectionState(
  songs: RecheckCandidateDTO[],
  selectedIds: ReadonlySet<number>,
): SelectionState {
  const selectable = selectableSongIds(songs);
  const selectedCount = selectable.filter((songId) => selectedIds.has(songId)).length;
  if (selectedCount === 0) return "none";
  return selectedCount === selectable.length ? "all" : "some";
}

export function selectedCount(
  songs: RecheckCandidateDTO[],
  selectedIds: ReadonlySet<number>,
): number {
  return selectableSongIds(songs).filter((songId) => selectedIds.has(songId)).length;
}

export function toggleSongs(
  selectedIds: ReadonlySet<number>,
  songIds: number[],
  selected: boolean,
): Set<number> {
  const next = new Set(selectedIds);
  for (const songId of songIds) {
    if (selected) next.add(songId);
    else next.delete(songId);
  }
  return next;
}

export function songCountLabel(count: number): string {
  return `${count.toLocaleString()} ${count === 1 ? "song" : "songs"}`;
}
