const YOUTUBE_ANDROID_PACKAGE = "com.google.android.youtube";
const ANDROID_USER_AGENT_PATTERN = /Android/i;
const MOBILE_USER_AGENT_PATTERN = /Android|iPhone|iPad|iPod/i;
const CHROMIUM_USER_AGENT_PATTERN = /Chrome|Chromium|CriOS|Edg\//i;
const NON_CAPTURING_USER_AGENT_PATTERN = /Firefox|FxiOS/i;
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

// Only desktop Chromium browsers (Chrome, Edge, Opera, Brave) return an audio track from a
// screen share; Firefox and Safari return none, so those players cannot DJ. Phones hand
// playback to the YouTube app and play it out loud in the room instead.
export function canShareTabAudio(userAgent: string): boolean {
  if (MOBILE_USER_AGENT_PATTERN.test(userAgent) || NON_CAPTURING_USER_AGENT_PATTERN.test(userAgent)) return false;
  return CHROMIUM_USER_AGENT_PATTERN.test(userAgent);
}
