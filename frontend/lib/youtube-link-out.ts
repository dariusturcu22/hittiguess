const YOUTUBE_ANDROID_PACKAGE = "com.google.android.youtube";
const NEW_TAB_TARGET = "_blank";
const CURRENT_TAB_TARGET = "_self";
const WINDOW_FEATURES = "noopener,noreferrer";

function isAndroid(userAgent: string): boolean {
  return /Android/i.test(userAgent);
}

export function openYoutubeLink(watchUrl: string): boolean {
  let openedWindow: Window | null;
  if (isAndroid(navigator.userAgent)) {
    const fallbackUrl = encodeURIComponent(watchUrl);
    openedWindow = window.open(
      `intent://${watchUrl.replace(/^https?:\/\//, "")}#Intent;package=${YOUTUBE_ANDROID_PACKAGE};scheme=https;S.browser_fallback_url=${fallbackUrl};end`,
      NEW_TAB_TARGET,
      WINDOW_FEATURES,
    );
  } else {
    openedWindow = window.open(watchUrl, NEW_TAB_TARGET, WINDOW_FEATURES);
  }

  if (openedWindow) return true;
  window.open(watchUrl, CURRENT_TAB_TARGET, WINDOW_FEATURES);
  return false;
}
