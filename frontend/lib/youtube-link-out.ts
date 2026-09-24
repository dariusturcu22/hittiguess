const YOUTUBE_ANDROID_PACKAGE = "com.google.android.youtube";
const ANDROID_USER_AGENT_PATTERN = /Android/i;
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
