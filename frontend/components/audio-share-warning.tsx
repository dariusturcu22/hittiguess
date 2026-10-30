"use client";

import { useState } from "react";
import { TriangleAlert, X } from "lucide-react";

import {
  AUDIO_SHARE_WARNING_MESSAGES,
  audioShareWarningKind,
  dismissAudioShareWarning,
  isAudioShareWarningDismissed,
  type AudioShareWarningInput,
} from "@/lib/audio-share-warning";
import { canShareSongAudio } from "@/lib/youtube-link-out";

export function useBrowserCanShareAudio(): boolean {
  return typeof navigator === "undefined" || canShareSongAudio(navigator.userAgent, navigator.maxTouchPoints);
}

// Compact and dismissible for the rest of the browser session, so it never nags during play.
export function AudioShareWarning(input: AudioShareWarningInput) {
  const kind = audioShareWarningKind(input);
  const [dismissedKinds, setDismissedKinds] = useState<ReadonlySet<string>>(() => new Set());
  if (kind === null || dismissedKinds.has(kind) || isAudioShareWarningDismissed(kind)) return null;
  return (
    <div role="status" className="flex max-w-[520px] items-start gap-2 rounded-2xl border-2 border-warning bg-warning/10 px-3.5 py-2 text-[11px] font-bold leading-snug text-warning">
      <TriangleAlert className="mt-px size-3.5 shrink-0" />
      <span className="flex-1">{AUDIO_SHARE_WARNING_MESSAGES[kind]}</span>
      <button
        type="button"
        aria-label="Dismiss audio warning"
        onClick={() => {
          dismissAudioShareWarning(kind);
          setDismissedKinds((previous) => new Set(previous).add(kind));
        }}
        className="shrink-0 rounded-full p-0.5 transition-colors hover:bg-warning/20"
      ><X className="size-3.5" /></button>
    </div>
  );
}
