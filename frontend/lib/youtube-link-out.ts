const YOUTUBE_ANDROID_PACKAGE = "com.google.android.youtube";

function isAndroid(userAgent: string): boolean {
  return /Android/i.test(userAgent);
}

export function openYoutubeLink(watchUrl: string) {
  if (isAndroid(navigator.userAgent)) {
    const fallbackUrl = encodeURIComponent(watchUrl);
    window.location.assign(`intent://${watchUrl.replace(/^https?:\/\//, "")}#Intent;package=${YOUTUBE_ANDROID_PACKAGE};scheme=https;S.browser_fallback_url=${fallbackUrl};end`);
    return;
  }

  window.open(watchUrl, "_blank", "noopener,noreferrer");
}
