export type AudioShareWarningKind = "own-browser" | "own-browser-is-dj" | "dj-cannot-share";

export interface AudioShareWarningInput {
  canShareAudio: boolean;
  isDj: boolean;
  djCanShareAudio: boolean | undefined;
}

export const AUDIO_SHARE_WARNING_MESSAGES: Record<AudioShareWarningKind, string> = {
  "own-browser": "This browser can't share the song's audio, so you are skipped as DJ whenever someone else can. To DJ, use Chrome or Edge on a Windows computer or a Mac.",
  "own-browser-is-dj": "This browser can't share the song's audio and nobody else in the group can either, so the others will not hear the song. Play it out loud for the room, or switch to Chrome or Edge on a Windows computer or a Mac.",
  "dj-cannot-share": "The DJ's browser can't share the song's audio, so it will not play on your device. The DJ plays it out loud for the room.",
};

export const AUDIO_SHARE_WARNING_DISMISSED_KEY = "hittiguess.audioShareWarningDismissed";

// A player whose own browser cannot share is always told first; the DJ-side warning is only
// for players who could hear a share but have none coming.
export function audioShareWarningKind({ canShareAudio, isDj, djCanShareAudio }: AudioShareWarningInput): AudioShareWarningKind | null {
  if (!canShareAudio) return isDj ? "own-browser-is-dj" : "own-browser";
  if (!isDj && djCanShareAudio === false) return "dj-cannot-share";
  return null;
}

export function isAudioShareWarningDismissed(kind: AudioShareWarningKind): boolean {
  try {
    return window.sessionStorage.getItem(`${AUDIO_SHARE_WARNING_DISMISSED_KEY}.${kind}`) !== null;
  } catch {
    return false;
  }
}

export function dismissAudioShareWarning(kind: AudioShareWarningKind): void {
  try {
    window.sessionStorage.setItem(`${AUDIO_SHARE_WARNING_DISMISSED_KEY}.${kind}`, "1");
  } catch {
    // Storage can be blocked; the warning then just comes back on the next page.
  }
}
