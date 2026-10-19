export const PLAYLIST_COLOR_PRESETS = ["cba6f7", "fab387", "a6e3a1", "89b4fa", "f5c2e7", "f9e2af"] as const;

export const DEFAULT_PLAYLIST_COLOR = PLAYLIST_COLOR_PRESETS[0];

export function playlistTitleColor(color: string | null | undefined): string {
  return `#${color ?? DEFAULT_PLAYLIST_COLOR}`;
}
