const YOUTUBE_ANDROID_PACKAGE = "com.google.android.youtube";
const ANDROID_USER_AGENT_PATTERN = /Android/i;
const MOBILE_USER_AGENT_PATTERN = /Android|iPhone|iPad|iPod/i;
const YOUTUBE_WINDOW_NAME = "hittiguess-youtube";
const YOUTUBE_WINDOW_FEATURES = "popup,width=960,height=640";
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

// Desktop DJs share the YouTube tab's audio into the voice call; phones hand playback to
// the YouTube app and play it out loud in the room instead.
export function canShareTabAudio(userAgent: string): boolean {
  return !MOBILE_USER_AGENT_PATTERN.test(userAgent);
}

// Opens YouTube in its own window rather than a tab, so the game window, and the
// audio-share picker the same click just raised in it, stays in view. The opener link is
// cut so the YouTube page can't navigate the game. Returns false when the browser
// blocked the window, leaving the anchor's own navigation as the fallback.
export function openYoutubeWindow(watchUrl: string): boolean {
  const youtubeWindow = window.open(watchUrl, YOUTUBE_WINDOW_NAME, YOUTUBE_WINDOW_FEATURES);
  if (!youtubeWindow) return false;
  youtubeWindow.opener = null;
  return true;
}
