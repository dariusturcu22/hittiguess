"use client";

import { useCallback, useEffect } from "react";

import { useReportAudioStatus } from "@/hooks/generated/game-session/game-session";
import { useAudioShareTest, type AudioShareTest } from "@/hooks/use-audio-share-test";
import { NOT_SHARED_STATUS, OK_STATUS, type ReportableAudioShareStatus } from "@/lib/audio-check";

export interface SessionAudioShareOptions {
  sessionId: number;
  // The game needs this player's share: every remaining player in a rotating game who can
  // share, only the DJ in a fixed one.
  isRequiredSharer: boolean;
  // The player's share as the server last recorded it.
  audioStatus: string | undefined;
  // The game is paused waiting for this player's new share.
  isPausedForCurrentPlayer: boolean;
  hasSessionEnded: boolean;
}

// Watches the share a required DJ started on the audio check. When it is gone, because
// the share bar's Stop was clicked, the shared screen closed, or the page was reloaded,
// the server is told, which pauses the game; the re-share and re-test then run here.
export function useSessionAudioShare({ sessionId, isRequiredSharer, audioStatus, isPausedForCurrentPlayer, hasSessionEnded }: SessionAudioShareOptions): AudioShareTest {
  const { mutate: reportAudioStatus } = useReportAudioStatus();
  const reportStatus = useCallback((status: ReportableAudioShareStatus) => {
    reportAudioStatus({ sessionId, data: { status } });
  }, [reportAudioStatus, sessionId]);

  const shareTest = useAudioShareTest({ isActive: isPausedForCurrentPlayer && !hasSessionEnded, onStatus: reportStatus });
  const isCapturing = shareTest.isCapturing;

  useEffect(() => {
    if (hasSessionEnded || !isRequiredSharer || audioStatus !== OK_STATUS || isCapturing) return;
    reportStatus(NOT_SHARED_STATUS);
  }, [audioStatus, hasSessionEnded, isCapturing, isRequiredSharer, reportStatus]);

  return shareTest;
}
