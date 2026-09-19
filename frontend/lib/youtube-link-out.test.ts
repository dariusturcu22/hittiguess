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

  it("opens the Android intent in a new tab with an encoded browser fallback", () => {
    Object.defineProperty(navigator, "userAgent", {
      configurable: true,
      value: "Mozilla/5.0 (Linux; Android 14)",
    });
    const open = vi.spyOn(window, "open").mockReturnValue(null);

    openYoutubeLink(WATCH_URL);

    expect(open).toHaveBeenCalledWith(
      `intent://www.youtube.com/watch?v=dQw4w9WgXcQ#Intent;package=com.google.android.youtube;scheme=https;S.browser_fallback_url=${encodeURIComponent(WATCH_URL)};end`,
      "_blank",
      "noopener,noreferrer",
    );
  });
});
