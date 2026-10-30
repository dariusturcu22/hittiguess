const YOUTUBE_ANDROID_PACKAGE = "com.google.android.youtube";
const ANDROID_USER_AGENT_PATTERN = /Android/i;
const MOBILE_USER_AGENT_PATTERN = /Android|iPhone|iPad|iPod/i;
const FIREFOX_USER_AGENT_PATTERN = /Firefox|FxiOS/i;
const URL_SCHEME_PATTERN = /^https?:\/\//;

export const YOUTUBE_LINK_OUT_TARGET = "_blank";
export const YOUTUBE_LINK_OUT_REL = "noopener noreferrer";

// The DJ's link-out is a real anchor rather than window.open: a clicked link is never
// popup-blocked, and it can't fall back into navigating the game tab. Android gets an
// intent URL so the YouTube app claims it, with the web page as the browser fallback.
export function youtubeLinkOutHref(watchUrl: string, userAgent: string): string {
  if (!ANDROID_USER_AGENT_PATTERN.test(userAgent)) {
    return watchUrl;
  }
  const fallbackUrl = encodeURIComponent(watchUrl);
  const intentPath = watchUrl.replace(URL_SCHEME_PATTERN, "");
  return `intent://${intentPath}#Intent;package=${YOUTUBE_ANDROID_PACKAGE};scheme=https;S.browser_fallback_url=${fallbackUrl};end`;
}

// Chromium reports its major version in the Chrome/ token, including Edge, Opera, and Brave.
const CHROMIUM_VERSION_PATTERN = /Chrome\/(\d+)/;
const CHROME_OS_USER_AGENT_PATTERN = /CrOS/;
const WINDOWS_USER_AGENT_PATTERN = /Windows/i;
const MAC_USER_AGENT_PATTERN = /Macintosh|Mac OS X/i;
const FIRST_CHROMIUM_WITH_MAC_SYSTEM_AUDIO = 141;
const DESKTOP_MODE_TABLET_MIN_TOUCH_POINTS = 2;

// Phones and tablets: an iPad in desktop mode reports a Mac user agent, and only its touch
// points give it away.
function isMobileDevice(userAgent: string, maxTouchPoints: number): boolean {
  if (MOBILE_USER_AGENT_PATTERN.test(userAgent)) return true;
  return MAC_USER_AGENT_PATTERN.test(userAgent) && maxTouchPoints >= DESKTOP_MODE_TABLET_MIN_TOUCH_POINTS;
}

function chromiumMajorVersion(userAgent: string): number | null {
  const match = CHROMIUM_VERSION_PATTERN.exec(userAgent);
  if (!match) return null;
  const [, major] = match;
  return Number(major);
}

// The song is captured by sharing the entire screen with system audio, because the YouTube
// tab does not exist yet when the DJ is asked to share. Chromium only offers system audio
// for a screen share on Windows and ChromeOS, and on macOS from Chrome 141 (macOS 14.2 or
// newer, which a user agent cannot show). Linux Chromium offers tab audio only, so it is out.
// Firefox and Safari return no audio track from a screen share, and phones and tablets
// either lack getDisplayMedia or never return audio. The platform table is in GAME_DESIGN.md.
export function canShareSongAudio(userAgent: string, maxTouchPoints = 0): boolean {
  if (isMobileDevice(userAgent, maxTouchPoints)) return false;
  if (FIREFOX_USER_AGENT_PATTERN.test(userAgent)) return false;
  const majorVersion = chromiumMajorVersion(userAgent);
  if (majorVersion === null) return false;
  if (CHROME_OS_USER_AGENT_PATTERN.test(userAgent) || WINDOWS_USER_AGENT_PATTERN.test(userAgent)) return true;
  if (MAC_USER_AGENT_PATTERN.test(userAgent)) return majorVersion >= FIRST_CHROMIUM_WITH_MAC_SYSTEM_AUDIO;
  return false;
}
