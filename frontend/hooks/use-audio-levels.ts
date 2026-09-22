"use client";

import { useEffect, useRef, useState } from "react";

import { useLocalAudioStream } from "./use-local-audio-stream";

const VISUALIZER_BAR_COUNT = 5;
const SILENCE_LEVEL = 0;

interface AudioContextWindow extends Window {
  webkitAudioContext?: typeof AudioContext;
}

// Drives the unrevealed card's bars from the shared local audio: an analyser
// on the DJ's tab capture (or microphone) folds frequency data into one level
// per bar each animation frame. No stream means silence, and the card renders
// its static bars instead.
export function useAudioLevels(): number[] {
  const stream = useLocalAudioStream();
  const [levels, setLevels] = useState<number[]>(() => Array(VISUALIZER_BAR_COUNT).fill(SILENCE_LEVEL));
  const [trackedStream, setTrackedStream] = useState<MediaStream | null>(null);
  const frameReference = useRef<number | undefined>(undefined);
  const contextReference = useRef<AudioContext | undefined>(undefined);

  if (trackedStream !== stream) {
    setTrackedStream(stream);
    setLevels(Array(VISUALIZER_BAR_COUNT).fill(SILENCE_LEVEL));
  }

  useEffect(() => {
    if (!stream) {
      return;
    }
    const Context = window.AudioContext ?? (window as AudioContextWindow).webkitAudioContext;
    if (!Context) {
      return;
    }
    const context = new Context();
    contextReference.current = context;
    const source = context.createMediaStreamSource(stream);
    const analyser = context.createAnalyser();
    analyser.fftSize = 64;
    source.connect(analyser);
    const bins = new Uint8Array(analyser.frequencyBinCount);
    const binsPerBar = Math.max(1, Math.floor(bins.length / VISUALIZER_BAR_COUNT));

    const readLevels = () => {
      analyser.getByteFrequencyData(bins);
      const nextLevels: number[] = [];
      for (let barIndex = 0; barIndex < VISUALIZER_BAR_COUNT; barIndex += 1) {
        let total = 0;
        for (let binIndex = 0; binIndex < binsPerBar; binIndex += 1) {
          total += bins[(barIndex * binsPerBar + binIndex) % bins.length] ?? 0;
        }
        nextLevels.push(total / (binsPerBar * 255));
      }
      setLevels(nextLevels);
      frameReference.current = requestAnimationFrame(readLevels);
    };
    readLevels();

    return () => {
      if (frameReference.current !== undefined) {
        cancelAnimationFrame(frameReference.current);
      }
      void context.close().catch(() => {});
      contextReference.current = undefined;
    };
  }, [stream]);

  return levels;
}
