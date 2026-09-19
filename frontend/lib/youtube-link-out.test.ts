import { afterEach, describe, expect, it, vi } from "vitest";

import { openYoutubeLink } from "./youtube-link-out";

const WATCH_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("openYoutubeLink", () => {
  it("opens the normal YouTube URL in a new tab outside Android", () => {
    const open = vi.spyOn(window, "open").mockReturnValue(null);

    openYoutubeLink(WATCH_URL);

    expect(open).toHaveBeenCalledWith(WATCH_URL, "_blank", "noopener,noreferrer");
  });
});
