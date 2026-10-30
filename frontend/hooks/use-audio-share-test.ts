"use client";

import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";

import {
  advanceAudioTest,
  FAILED_STATUS,
  INITIAL_AUDIO_TEST_STATE,
  NOT_SHARED_STATUS,
  OK_STATUS,
  rootMeanSquare,
  SILENT_SHARE_TIMEOUT_MILLISECONDS,
  TESTING_STATUS,
  type ReportableAudioShareStatus,
} from "@/lib/audio-check";
import { getSongCaptureStream, startSongCapture, stopSongCapture, subscribeSongCapture, type SongCaptureErrorKind } from "@/lib/song-capture";

export const AUDIO_TEST_SAMPLE_INTERVAL_MILLISECONDS = 50;
const ANALYSER_FFT_SIZE = 2048;

interface AudioContextWindow extends Window {
  webkitAudioContext?: typeof AudioContext;
}

export interface AudioShareTestOptions {
  // The test runs and reports only while this is true: during the audio check for a
  // player who has to share, or while the game waits for this player's new share.
  isActive: boolean;
  // The server already holds this player's share as passed, so a share still running
  // from before, after a remount, is not tested again.
  isAlreadyPassed?: boolean;
  onStatus: (status: ReportableAudioShareStatus) => void;
}

export interface AudioShareTest {
  isCapturing: boolean;
  level: number;
  hasPassed: boolean;
  isSilent: boolean;
  shareErrorMessage: string | null;
  shareErrorKind: SongCaptureErrorKind | null;
  // Both must run straight from a click: the share picker needs the click's activation.
  share: () => Promise<void>;
  changeSharedScreen: () => Promise<void>;
}

// Shares the screen's audio once and tests it: an analyser on the shared track samples
// its level, and the test passes once the level stays above the threshold for half a
// second. A share with no audio track fails at once. Each step is reported through
// onStatus so the other players see it live.
export function useAudioShareTest({ isActive, isAlreadyPassed = false, onStatus }: AudioShareTestOptions): AudioShareTest {
  const captureStream = useSyncExternalStore(subscribeSongCapture, getSongCaptureStream, () => null);
  const [level, setLevel] = useState(0);
  const [passedStream, setPassedStream] = useState<MediaStream | null>(() => (isAlreadyPassed ? getSongCaptureStream() : null));
  const [silentStream, setSilentStream] = useState<MediaStream | null>(null);
  const [shareErrorMessage, setShareErrorMessage] = useState<string | null>(null);
  const [shareErrorKind, setShareErrorKind] = useState<SongCaptureErrorKind | null>(null);
  const onStatusReference = useRef(onStatus);
  const reportedStreamReference = useRef<MediaStream | null>(isAlreadyPassed ? getSongCaptureStream() : null);
  const hasPassed = captureStream !== null && passedStream === captureStream;

  useEffect(() => {
    onStatusReference.current = onStatus;
  });

  // A new stream is reported as being tested; a stream that ends after being reported is
  // a stopped share.
  useEffect(() => {
    if (!isActive) return;
    if (captureStream === null) {
      if (reportedStreamReference.current !== null) {
        reportedStreamReference.current = null;
        onStatusReference.current(NOT_SHARED_STATUS);
      }
      return;
    }
    if (reportedStreamReference.current === captureStream) return;
    reportedStreamReference.current = captureStream;
    onStatusReference.current(TESTING_STATUS);
  }, [captureStream, isActive]);

  useEffect(() => {
    if (!isActive || !captureStream || hasPassed) return;
    const Context = window.AudioContext ?? (window as AudioContextWindow).webkitAudioContext;
    if (!Context) return;
    const context = new Context();
    let source: MediaStreamAudioSourceNode;
    try {
      source = context.createMediaStreamSource(captureStream);
    } catch {
      void context.close().catch(() => {});
      return;
    }
    const analyser = context.createAnalyser();
    analyser.fftSize = ANALYSER_FFT_SIZE;
    source.connect(analyser);
    void context.resume().catch(() => {});
    const samples = new Float32Array(analyser.fftSize);
    let state = INITIAL_AUDIO_TEST_STATE;
    const sampleInterval = window.setInterval(() => {
      analyser.getFloatTimeDomainData(samples);
      const sampledLevel = rootMeanSquare(samples);
      state = advanceAudioTest(state, sampledLevel, Date.now());
      setLevel(sampledLevel);
      if (state.hasPassed) {
        window.clearInterval(sampleInterval);
        setPassedStream(captureStream);
        onStatusReference.current(OK_STATUS);
      }
    }, AUDIO_TEST_SAMPLE_INTERVAL_MILLISECONDS);
    const silenceTimeout = window.setTimeout(() => {
      if (state.hasPassed) return;
      setSilentStream(captureStream);
      onStatusReference.current(FAILED_STATUS);
    }, SILENT_SHARE_TIMEOUT_MILLISECONDS);
    return () => {
      window.clearInterval(sampleInterval);
      window.clearTimeout(silenceTimeout);
      setLevel(0);
      source.disconnect();
      analyser.disconnect();
      void context.close().catch(() => {});
    };
  }, [captureStream, hasPassed, isActive]);

  const share = useCallback(async () => {
    setShareErrorMessage(null);
    setShareErrorKind(null);
    const result = await startSongCapture();
    if (result.errorMessage === null) return;
    setShareErrorMessage(result.errorMessage);
    setShareErrorKind(result.errorKind ?? null);
    if (result.errorKind === "no-audio-track") onStatusReference.current(FAILED_STATUS);
  }, []);

  const changeSharedScreen = useCallback(async () => {
    stopSongCapture();
    await share();
  }, [share]);

  return {
    isCapturing: captureStream !== null,
    level,
    hasPassed,
    isSilent: captureStream !== null && silentStream === captureStream && !hasPassed,
    shareErrorMessage,
    shareErrorKind,
    share,
    changeSharedScreen,
  };
}
