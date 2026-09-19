import { afterEach, describe, expect, it, vi } from "vitest";

import { openYoutubeLink } from "./youtube-link-out";

const WATCH_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("openYoutubeLink", () => {
  it("opens the normal YouTube URL in a new tab outside Android", () => {
    const open = vi.spyOn(window, "open").mockReturnValue(null);

    expect(openYoutubeLink(WATCH_URL)).toBe(false);

    expect(open).toHaveBeenNthCalledWith(1, WATCH_URL, "_blank", "noopener,noreferrer");
    expect(open).toHaveBeenNthCalledWith(2, WATCH_URL, "_self", "noopener,noreferrer");
  });

  it("opens the Android intent in a new tab with an encoded browser fallback", () => {
    Object.defineProperty(navigator, "userAgent", {
      configurable: true,
      value: "Mozilla/5.0 (Linux; Android 14)",
    });
    const open = vi.spyOn(window, "open").mockReturnValue({} as Window);

    expect(openYoutubeLink(WATCH_URL)).toBe(true);

    expect(open).toHaveBeenCalledWith(
      `intent://www.youtube.com/watch?v=dQw4w9WgXcQ#Intent;package=com.google.android.youtube;scheme=https;S.browser_fallback_url=${encodeURIComponent(WATCH_URL)};end`,
      "_blank",
      "noopener,noreferrer",
    );
  });

  it("uses the universal YouTube link on iOS so the app can claim it or the browser can open it", () => {
    Object.defineProperty(navigator, "userAgent", {
      configurable: true,
      value: "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)",
    });
    const open = vi.spyOn(window, "open").mockReturnValue({} as Window);

    expect(openYoutubeLink(WATCH_URL)).toBe(true);

    expect(open).toHaveBeenCalledWith(WATCH_URL, "_blank", "noopener,noreferrer");
  });
});
