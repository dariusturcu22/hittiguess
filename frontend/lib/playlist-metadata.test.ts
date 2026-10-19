import { describe, expect, it } from "vitest";
import { formatPlaylistCreation, formatSongDuration } from "./playlist-metadata";

describe("playlist metadata display", () => {
  it("formats duration with padded seconds and supports long videos", () => {
    const shortDurationSeconds = 185;
    const longDurationSeconds = 3601;
    expect(formatSongDuration(shortDurationSeconds)).toBe("3:05");
    expect(formatSongDuration(longDurationSeconds)).toBe("60:01");
  });

  it("shows unavailable for missing or invalid duration", () => {
    expect(formatSongDuration(null)).toBe("Unavailable");
    expect(formatSongDuration(undefined)).toBe("Unavailable");
    expect(formatSongDuration(Number.NaN)).toBe("Unavailable");
    const invalidDurationSeconds = -1;
    expect(formatSongDuration(invalidDurationSeconds)).toBe("Unavailable");
  });

  it("formats creation dates in UTC without inventing legacy dates", () => {
    expect(formatPlaylistCreation("2026-10-09T23:30:00Z")).toBe("Created 9 Oct 2026");
    expect(formatPlaylistCreation(null)).toBe("Creation date unavailable");
    expect(formatPlaylistCreation("invalid")).toBe("Creation date unavailable");
  });
});
