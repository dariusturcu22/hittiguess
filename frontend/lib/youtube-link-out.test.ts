import { describe, expect, it } from "vitest";

import { canShareSongAudio, youtubeLinkOutHref } from "./youtube-link-out";

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

const WINDOWS_CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
const WINDOWS_EDGE = `${WINDOWS_CHROME} Edg/140.0.0.0`;
const WINDOWS_OPERA = `${WINDOWS_CHROME} OPR/120.0.0.0`;
const MAC_CHROME_140 = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
const MAC_CHROME_141 = MAC_CHROME_140.replace("Chrome/140", "Chrome/141");
const MAC_SAFARI = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15";
const LINUX_CHROME = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
const CHROME_OS_CHROME = "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
const IPAD_DESKTOP_SAFARI = MAC_SAFARI;
const IPAD_TOUCH_POINTS = 5;

describe("canShareSongAudio", () => {
  it("shares system audio from Chromium browsers on Windows and ChromeOS", () => {
    expect(canShareSongAudio(WINDOWS_CHROME)).toBe(true);
    expect(canShareSongAudio(WINDOWS_EDGE)).toBe(true);
    expect(canShareSongAudio(WINDOWS_OPERA)).toBe(true);
    expect(canShareSongAudio(CHROME_OS_CHROME)).toBe(true);
  });

  it("shares from Chromium on macOS only from the version that offers system audio", () => {
    expect(canShareSongAudio(MAC_CHROME_140)).toBe(false);
    expect(canShareSongAudio(MAC_CHROME_141)).toBe(true);
  });

  it("does not offer Chromium on Linux, which shares tab audio only", () => {
    expect(canShareSongAudio(LINUX_CHROME)).toBe(false);
  });

  it("does not offer Safari, whose screen share carries no audio", () => {
    expect(canShareSongAudio(MAC_SAFARI)).toBe(false);
  });

  it("does not offer desktop Firefox, whose screen share carries no audio", () => {
    expect(canShareSongAudio("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")).toBe(false);
    expect(canShareSongAudio("Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0")).toBe(false);
    expect(canShareSongAudio("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:140.0) Gecko/20100101 Firefox/140.0")).toBe(false);
  });

  it("does not offer phones or tablets", () => {
    expect(canShareSongAudio("Mozilla/5.0 (Linux; Android 14) Chrome/140.0 Mobile")).toBe(false);
    expect(canShareSongAudio("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)")).toBe(false);
    expect(canShareSongAudio("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) CriOS/140.0 Mobile/15E148 Safari/604.1")).toBe(false);
    expect(canShareSongAudio("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) FxiOS/140.0 Mobile/15E148 Safari/605.1.15")).toBe(false);
  });

  it("treats an iPad reporting a Mac user agent as a tablet", () => {
    expect(canShareSongAudio(IPAD_DESKTOP_SAFARI, IPAD_TOUCH_POINTS)).toBe(false);
    expect(canShareSongAudio(MAC_CHROME_141, IPAD_TOUCH_POINTS)).toBe(false);
  });
});
