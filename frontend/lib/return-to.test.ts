import { describe, expect, it } from "vitest";

import { safeReturnToPath } from "./return-to";

describe("safeReturnToPath", () => {
  it("accepts same-origin absolute paths", () => {
    expect(safeReturnToPath("/playlists/join/abc123")).toBe("/playlists/join/abc123");
    expect(safeReturnToPath("/playlists")).toBe("/playlists");
  });

  it("rejects missing, relative, off-site, and protocol-relative targets", () => {
    expect(safeReturnToPath(null)).toBeNull();
    expect(safeReturnToPath("")).toBeNull();
    expect(safeReturnToPath("playlists/join/abc")).toBeNull();
    expect(safeReturnToPath("https://evil.example.com")).toBeNull();
    expect(safeReturnToPath("//evil.example.com/playlists")).toBeNull();
    expect(safeReturnToPath("/\\evil.example.com/playlists")).toBeNull();
  });
});
