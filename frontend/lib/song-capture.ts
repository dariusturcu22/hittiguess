import { recordAudioShareOutcome } from "@/lib/audio-share-outcome";

// The DJ's screen capture outlives the session page component: a match keeps its
// capture across in-app navigation, so the share prompt appears once per match. The
// hook that owns the session page reads this module's state and calls stopSongCapture
// when the match is over, the player is gone, or the share ends in the browser bar.
export const SONG_AUDIO_CONTENT_HINT = "music";

const CHANNEL_COUNT_STEREO = 2;
const SELF_BROWSER_SURFACE = "exclude";
const SYSTEM_AUDIO = "include";
const UNKNOWN_BROWSER_NAME = "This browser";
const UNKNOWN_PLATFORM_NAME = "unknown";

const MISSING_AUDIO_SUFFIX = " shared no audio. Choose Entire screen and turn on Share system audio, then try again.";
const SHARE_UNSUPPORTED_MESSAGE = "This browser can't share screen audio.";
const SHARE_INSECURE_CONTEXT_MESSAGE = "Sharing audio needs the game open over HTTPS.";
const SHARE_CANCELLED_MESSAGE = "The audio share was cancelled. Try again.";

const BROWSER_NAMES: ReadonlyArray<readonly [RegExp, string]> = [
  [/Edg\//i, "Edge"],
  [/OPR\//i, "Opera"],
  [/SamsungBrowser/i, "Samsung Internet"],
  [/Chrome|CriOS/i, "Chrome"],
  [/Firefox|FxiOS/i, "Firefox"],
  [/Safari/i, "Safari"],
];

const PLATFORM_PATTERNS: ReadonlyArray<readonly [RegExp, string]> = [
  [/Android/i, "Android"],
  [/iPhone|iPad|iPod/i, "iOS"],
  [/Windows/i, "Windows"],
  [/Macintosh|Mac OS X/i, "macOS"],
  [/Linux/i, "Linux"],
];

// Chromium-only display-media hints: exclude the game tab itself from the picker and
// offer system audio, so the DJ shares the whole screen's audio (or the YouTube tab's).
// Whole-screen system audio would also carry the game tab's own output, which is every
// other player's voice, so restrictOwnAudio keeps that tab out of the captured track.
interface SongAudioConstraints extends MediaTrackConstraints {
  restrictOwnAudio?: boolean;
}

interface SongDisplayConstraints extends DisplayMediaStreamOptions {
  audio: SongAudioConstraints;
  selfBrowserSurface?: "exclude" | "include";
  systemAudio?: "exclude" | "include";
}

export interface SongCaptureResult {
  stream: MediaStream | null;
  errorMessage: string | null;
}

let capturedStream: MediaStream | null = null;
let capturedTrack: MediaStreamTrack | null = null;
const captureListeners = new Set<() => void>();

function emitCapture() {
  captureListeners.forEach((listener) => listener());
}

export function subscribeSongCapture(listener: () => void): () => void {
  captureListeners.add(listener);
  return () => {
    captureListeners.delete(listener);
  };
}

export function getSongCaptureStream(): MediaStream | null {
  return capturedStream;
}

export function getSongCaptureTrack(): MediaStreamTrack | null {
  return capturedTrack;
}

export function describeBrowser(userAgent: string): string {
  for (const [pattern, name] of BROWSER_NAMES) {
    if (pattern.test(userAgent)) return name;
  }
  return UNKNOWN_BROWSER_NAME;
}

export function describePlatform(userAgent: string, platform: string): string {
  if (platform) return platform;
  for (const [pattern, name] of PLATFORM_PATTERNS) {
    if (pattern.test(userAgent)) return name;
  }
  return UNKNOWN_PLATFORM_NAME;
}

export function stopSongCapture() {
  if (!capturedStream) return;
  capturedTrack?.removeEventListener("ended", stopSongCapture);
  capturedStream.getTracks().forEach((track) => track.stop());
  capturedStream = null;
  capturedTrack = null;
  if (typeof window !== "undefined") {
    window.removeEventListener("pagehide", stopSongCapture);
    window.removeEventListener("unload", stopSongCapture);
  }
  emitCapture();
}

// Must run straight from a click: getDisplayMedia needs the click's user activation, so
// nothing is awaited before it. A cancelled or track-less attempt is recorded so the
// results from different browsers can be compared.
export async function startSongCapture(): Promise<SongCaptureResult> {
  if (capturedStream) {
    return { stream: capturedStream, errorMessage: null };
  }
  const mediaDevices = typeof navigator === "undefined" ? undefined : navigator.mediaDevices;
  if (!mediaDevices?.getDisplayMedia) {
    // Browsers hide screen capture entirely on an insecure origin, which is what a
    // plain-HTTP LAN address is, so that case gets its own explanation.
    const isInsecureContext = typeof window !== "undefined" && !window.isSecureContext;
    return { stream: null, errorMessage: isInsecureContext ? SHARE_INSECURE_CONTEXT_MESSAGE : SHARE_UNSUPPORTED_MESSAGE };
  }
  const userAgent = typeof navigator === "undefined" ? "" : navigator.userAgent;
  const platform = describePlatform(userAgent, typeof navigator === "undefined" ? "" : navigator.platform);
  const browser = describeBrowser(userAgent);
  const constraints: SongDisplayConstraints = {
    video: true,
    audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false, channelCount: CHANNEL_COUNT_STEREO, restrictOwnAudio: true },
    selfBrowserSurface: SELF_BROWSER_SURFACE,
    systemAudio: SYSTEM_AUDIO,
  };
  let displayStream: MediaStream;
  try {
    displayStream = await mediaDevices.getDisplayMedia(constraints);
  } catch {
    recordAudioShareOutcome({ browser, platform, displaySurface: null, audioTrackCount: 0 });
    return { stream: null, errorMessage: SHARE_CANCELLED_MESSAGE };
  }
  const [videoTrack] = displayStream.getVideoTracks();
  const displaySurface = videoTrack?.getSettings().displaySurface ?? null;
  const audioTracks = displayStream.getAudioTracks();
  recordAudioShareOutcome({ browser, platform, displaySurface, audioTrackCount: audioTracks.length });
  displayStream.getVideoTracks().forEach((track) => track.stop());
  const [audioTrack] = audioTracks;
  if (!audioTrack) {
    displayStream.getTracks().forEach((track) => track.stop());
    return { stream: null, errorMessage: `${browser}${MISSING_AUDIO_SUFFIX}` };
  }
  audioTrack.contentHint = SONG_AUDIO_CONTENT_HINT;
  capturedTrack = audioTrack;
  capturedStream = new MediaStream([audioTrack]);
  audioTrack.addEventListener("ended", stopSongCapture);
  if (typeof window !== "undefined") {
    window.addEventListener("pagehide", stopSongCapture);
    window.addEventListener("unload", stopSongCapture);
  }
  emitCapture();
  return { stream: capturedStream, errorMessage: null };
}
