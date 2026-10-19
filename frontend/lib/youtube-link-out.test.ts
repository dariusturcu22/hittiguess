import { describe, expect, it } from "vitest";

import { canShareTabAudio, youtubeLinkOutHref } from "./youtube-link-out";

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

describe("canShareTabAudio", () => {
  it("shares audio from desktop Chromium browsers", () => {
    expect(canShareTabAudio("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36")).toBe(true);
    expect(canShareTabAudio("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36 Edg/140.0")).toBe(true);
  });

  it("lets desktop Firefox and Safari try to capture audio while the flag is set", () => {
    expect(canShareTabAudio("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")).toBe(true);
    expect(canShareTabAudio("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/18.0 Safari/605.1.15")).toBe(true);
  });

  it("does not capture audio on phones", () => {
    expect(canShareTabAudio("Mozilla/5.0 (Linux; Android 14) Chrome/140.0 Mobile")).toBe(false);
    expect(canShareTabAudio("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)")).toBe(false);
  });
});
