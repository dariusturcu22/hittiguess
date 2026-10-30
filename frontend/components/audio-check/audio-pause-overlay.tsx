"use client";

import { Pause } from "lucide-react";

import { OwnShareCard } from "@/components/audio-check/audio-check-parts";
import type { AudioShareTest } from "@/hooks/use-audio-share-test";

export interface AudioPauseOverlayProps {
  // Display names of the players the game is waiting for, in turn order.
  waitingForNames: string[];
  isWaitingForCurrentPlayer: boolean;
  shareTest: AudioShareTest;
}

export function stoppedSharingMessage(waitingForNames: string[]): string {
  const names = waitingForNames.length > 0 ? waitingForNames.join(" and ") : "A DJ";
  return `${names} stopped sharing, waiting for them to share again`;
}

// The game is paused because a DJ it needs has no working audio share. That player gets
// the share and the sound test inline; everyone else sees who the game is waiting for.
export function AudioPauseOverlay({ waitingForNames, isWaitingForCurrentPlayer, shareTest }: AudioPauseOverlayProps) {
  return <div className="absolute inset-0 z-40 overflow-y-auto bg-background/90 px-4 py-8 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="audio-pause-title">
    <div className="mx-auto flex max-w-[680px] flex-col items-center text-center">
      <span className="flex size-12 items-center justify-center rounded-full border-[3px] border-warning bg-card"><Pause className="size-5 fill-warning text-warning" /></span>
      <h2 id="audio-pause-title" className="mt-4 font-display text-2xl text-foreground [text-shadow:3px_3px_0_var(--text-shadow-on-page)] sm:text-[32px]">{isWaitingForCurrentPlayer ? "Share your audio again" : "Game paused"}</h2>
      <p role="status" className="mt-3 text-[15px] leading-normal text-muted-foreground">{isWaitingForCurrentPlayer
        ? "Your audio share stopped, so the game is paused for everyone. Share your screen with system audio again and play a sound to test it. The game goes on once the test passes."
        : stoppedSharingMessage(waitingForNames)}</p>
    </div>
    {isWaitingForCurrentPlayer ? <OwnShareCard shareTest={shareTest} /> : null}
  </div>;
}
