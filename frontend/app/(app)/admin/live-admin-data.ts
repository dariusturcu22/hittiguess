import { keepPreviousData } from "@tanstack/react-query";

const ADMIN_QUERY_PREFIX = "/api/admin/";
const MILLISECONDS_PER_SECOND = 1_000;
const SECONDS_PER_MINUTE = 60;
const MINUTES_PER_HOUR = 60;
const JUST_NOW_SECONDS = 2;

export const UPDATED_AGO_TICK_MILLISECONDS = 1_000;

// Previous data stays visible during polling and page changes.
export function livePollingQueryOptions(refetchIntervalMilliseconds: number) {
  return {
    refetchInterval: refetchIntervalMilliseconds,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
    staleTime: 0,
    placeholderData: keepPreviousData,
  } as const;
}

export function isAdminQueryKey(queryKey: readonly unknown[]) {
  const [path] = queryKey;
  return typeof path === "string" && path.startsWith(ADMIN_QUERY_PREFIX);
}

export function formatUpdatedAgo(
  updatedAtMilliseconds: number | undefined,
  nowMilliseconds: number,
) {
  if (!updatedAtMilliseconds) {
    return null;
  }
  const elapsedSeconds = Math.max(
    0,
    Math.floor((nowMilliseconds - updatedAtMilliseconds) / MILLISECONDS_PER_SECOND),
  );
  if (elapsedSeconds < JUST_NOW_SECONDS) {
    return "Updated just now";
  }
  if (elapsedSeconds < SECONDS_PER_MINUTE) {
    return `Updated ${elapsedSeconds}s ago`;
  }
  const elapsedMinutes = Math.floor(elapsedSeconds / SECONDS_PER_MINUTE);
  if (elapsedMinutes < MINUTES_PER_HOUR) {
    return `Updated ${elapsedMinutes} min ago`;
  }
  return `Updated ${Math.floor(elapsedMinutes / MINUTES_PER_HOUR)} h ago`;
}
