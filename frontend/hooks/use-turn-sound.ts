"use client";

import { useCallback, useEffect, useRef } from "react";

const SOUND_FREQUENCY_HERTZ = 660;
const SOUND_DURATION_SECONDS = 0.25;
const SOUND_VOLUME = 0.12;
const SILENT_VOLUME = 0.001;
const RUNNING_AUDIO_CONTEXT_STATE = "running";

export function useTurnSound() {
  const contextReference = useRef<AudioContext | null>(null);
  useEffect(() => {
    function enableSound() {
      if (!window.AudioContext) return;
      contextReference.current ??= new AudioContext();
      void contextReference.current.resume().catch(() => {});
    }
    window.addEventListener("pointerdown", enableSound);
    window.addEventListener("keydown", enableSound);
    return () => {
      window.removeEventListener("pointerdown", enableSound);
      window.removeEventListener("keydown", enableSound);
      void contextReference.current?.close().catch(() => {});
      contextReference.current = null;
    };
  }, []);
  return useCallback(() => {
    const context = contextReference.current;
    if (!context || context.state !== RUNNING_AUDIO_CONTEXT_STATE) return;
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    oscillator.frequency.value = SOUND_FREQUENCY_HERTZ;
    gain.gain.setValueAtTime(SOUND_VOLUME, context.currentTime);
    gain.gain.exponentialRampToValueAtTime(SILENT_VOLUME, context.currentTime + SOUND_DURATION_SECONDS);
    oscillator.connect(gain);
    gain.connect(context.destination);
    oscillator.start();
    oscillator.stop(context.currentTime + SOUND_DURATION_SECONDS);
    oscillator.onended = () => { oscillator.disconnect(); gain.disconnect(); };
  }, []);
}
