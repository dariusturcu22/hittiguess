const SECONDS_PER_MINUTE = 60;
const DURATION_DIGIT_WIDTH = 2;

export function formatSongDuration(durationSeconds?: number | null): string {
  if (durationSeconds == null || !Number.isFinite(durationSeconds) || durationSeconds <= 0) {
    return "Unavailable";
  }
  const wholeSeconds = Math.floor(durationSeconds);
  const minutes = Math.floor(wholeSeconds / SECONDS_PER_MINUTE);
  const seconds = wholeSeconds % SECONDS_PER_MINUTE;
  return `${minutes}:${String(seconds).padStart(DURATION_DIGIT_WIDTH, "0")}`;
}

export function formatPlaylistCreation(createdAt?: string | null): string {
  if (!createdAt || Number.isNaN(Date.parse(createdAt))) {
    return "Creation date unavailable";
  }
  return `Created ${new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" }).format(new Date(createdAt))}`;
}
