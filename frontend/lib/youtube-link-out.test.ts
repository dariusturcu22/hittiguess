import { describe, expect, it } from "vitest";

import { youtubeLinkOutHref } from "./youtube-link-out";

const WATCH_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
const DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64)";
const ANDROID_USER_AGENT = "Mozilla/5.0 (Linux; Android 14)";
const IOS_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)";

describe("youtubeLinkOutHref", () => {
  it("links straight to the YouTube page on desktop", () => {
    expect(youtubeLinkOutHref(WATCH_URL, DESKTOP_USER_AGENT)).toBe(WATCH_URL);
  });

  it("builds an Android intent with an encoded browser fallback", () => {
    expect(youtubeLinkOutHref(WATCH_URL, ANDROID_USER_AGENT)).toBe(
      `intent://www.youtube.com/watch?v=dQw4w9WgXcQ#Intent;package=com.google.android.youtube;scheme=https;S.browser_fallback_url=${encodeURIComponent(WATCH_URL)};end`,
    );
  });

  it("uses the universal YouTube link on iOS so the app can claim it", () => {
    expect(youtubeLinkOutHref(WATCH_URL, IOS_USER_AGENT)).toBe(WATCH_URL);
  });
});
