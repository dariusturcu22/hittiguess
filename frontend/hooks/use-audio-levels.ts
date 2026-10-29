"use client";

import { useEffect, useState, useSyncExternalStore } from "react";

export const VISUALIZER_BAND_COUNT = 5;
export const SILENCE_FLOOR_LEVEL = 0;

const FFT_SIZE = 2048;
const SMOOTHING_TIME_CONSTANT = 0.8;
const MINIMUM_DECIBELS = -90;
const MAXIMUM_DECIBELS = -10;
const LOWEST_BAND_FREQUENCY_HZ = 40;
const HIGHEST_BAND_FREQUENCY_HZ = 16000;
const SPECTRAL_TILT_DECIBELS_PER_OCTAVE = 3;
const ATTACK_RATIO = 0.6;
const RELEASE_RATIO = 0.12;
const MINIMUM_LEVEL = 0;
const MAXIMUM_LEVEL = 1;
const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";

interface AudioContextWindow extends Window {
  webkitAudioContext?: typeof AudioContext;
}

function silentLevels(): number[] {
  return Array.from({ length: VISUALIZER_BAND_COUNT }, () => SILENCE_FLOOR_LEVEL);
}

function clampLevel(level: number): number {
  return Math.min(MAXIMUM_LEVEL, Math.max(MINIMUM_LEVEL, level));
}

function clampDecibels(decibels: number): number {
  return Number.isFinite(decibels) ? Math.max(decibels, MINIMUM_DECIBELS) : MINIMUM_DECIBELS;
}

function boundedBinIndex(frequencyHertz: number, binWidthHertz: number, binCount: number): number {
  const binIndex = Math.round(frequencyHertz / binWidthHertz);
  return Math.min(binCount - 1, Math.max(0, binIndex));
}

function isSilentSpectrum(frequencyData: Float32Array): boolean {
  for (const decibels of frequencyData) {
    if (decibels > MINIMUM_DECIBELS) return false;
  }
  return true;
}

// Band edges in hertz, lowest to highest, one more edge than bands, log-spaced
// between the lowest band frequency and the top of the spectrum, clamped to the
// Nyquist frequency of the sample rate.
export function bandFrequencyEdges(sampleRate: number): number[] {
  const nyquistFrequency = Math.max(sampleRate / 2, LOWEST_BAND_FREQUENCY_HZ);
  const highestFrequency = Math.min(HIGHEST_BAND_FREQUENCY_HZ, nyquistFrequency);
  const lowestFrequency = Math.min(LOWEST_BAND_FREQUENCY_HZ, highestFrequency);
  const octaveSpan = Math.log2(highestFrequency / lowestFrequency);
  return Array.from(
    { length: VISUALIZER_BAND_COUNT + 1 },
    (_, edgeIndex) => lowestFrequency * 2 ** ((octaveSpan * edgeIndex) / VISUALIZER_BAND_COUNT),
  );
}

// Folds one frame of AnalyserNode decibel data into per-band levels in 0..1.
// Each band averages the bins inside its log-spaced frequency range, gets a
// fixed decibel tilt so the higher bands stay visible against a flat spectrum,
// and moves toward that target with a fast rise and a slower fall.
export function computeBandLevels(frequencyData: Float32Array, sampleRate: number, previousLevels: number[]): number[] {
  if (frequencyData.length === 0 || isSilentSpectrum(frequencyData)) {
    return silentLevels();
  }
  const edges = bandFrequencyEdges(sampleRate);
  const binWidthHertz = sampleRate / 2 / frequencyData.length;
  return Array.from({ length: VISUALIZER_BAND_COUNT }, (_, bandIndex) => {
    const lowEdgeHertz = edges[bandIndex];
    const highEdgeHertz = edges[bandIndex + 1];
    const firstBinIndex = boundedBinIndex(lowEdgeHertz, binWidthHertz, frequencyData.length);
    const lastBinIndex = Math.max(firstBinIndex, boundedBinIndex(highEdgeHertz, binWidthHertz, frequencyData.length) - 1);
    let decibelTotal = 0;
    for (let binIndex = firstBinIndex; binIndex <= lastBinIndex; binIndex += 1) {
      decibelTotal += clampDecibels(frequencyData[binIndex]);
    }
    const averageDecibels = decibelTotal / (lastBinIndex - firstBinIndex + 1);
    const centerFrequencyHertz = Math.sqrt(lowEdgeHertz * highEdgeHertz);
    const tiltDecibels = SPECTRAL_TILT_DECIBELS_PER_OCTAVE * Math.log2(centerFrequencyHertz / LOWEST_BAND_FREQUENCY_HZ);
    const normalizedDecibels = (averageDecibels + tiltDecibels - MINIMUM_DECIBELS) / (MAXIMUM_DECIBELS - MINIMUM_DECIBELS);
    const targetLevel = averageDecibels <= MINIMUM_DECIBELS ? SILENCE_FLOOR_LEVEL : clampLevel(normalizedDecibels);
    const rawPreviousLevel = previousLevels[bandIndex];
    const previousLevel = Number.isFinite(rawPreviousLevel) ? clampLevel(rawPreviousLevel) : SILENCE_FLOOR_LEVEL;
    const smoothingRatio = targetLevel > previousLevel ? ATTACK_RATIO : RELEASE_RATIO;
    return previousLevel + (targetLevel - previousLevel) * smoothingRatio;
  });
}

function canReadMediaQuery(): boolean {
  return typeof window !== "undefined" && typeof window.matchMedia === "function";
}

function readPrefersReducedMotion(): boolean {
  return canReadMediaQuery() && window.matchMedia(REDUCED_MOTION_QUERY).matches;
}

function subscribeToPrefersReducedMotion(onChange: () => void): () => void {
  if (!canReadMediaQuery()) return () => {};
  const mediaQuery = window.matchMedia(REDUCED_MOTION_QUERY);
  mediaQuery.addEventListener("change", onChange);
  return () => mediaQuery.removeEventListener("change", onChange);
}

// Drives the unrevealed card's visualizer from the song stream: an analyser
// folds frequency data into one level per band each animation frame. The
// microphone and the voice mesh are never read. No stream, no analyser, or a
// spectrum at rest leaves every band on the silence floor so the card renders
// its static bars, and reduced motion never starts the animation frame loop.
export function useAudioLevels(stream: MediaStream | null): number[] {
  const [levels, setLevels] = useState<number[]>(silentLevels);
  const prefersReducedMotion = useSyncExternalStore(subscribeToPrefersReducedMotion, readPrefersReducedMotion, readPrefersReducedMotion);

  useEffect(() => {
    setLevels(silentLevels());
    if (!stream || prefersReducedMotion) {
      return;
    }
    const Context = window.AudioContext ?? (window as AudioContextWindow).webkitAudioContext;
    if (!Context) {
      return;
    }
    const context = new Context();
    let source: MediaStreamAudioSourceNode;
    try {
      source = context.createMediaStreamSource(stream);
    } catch {
      void context.close().catch(() => {});
      return;
    }
    const analyser = context.createAnalyser();
    analyser.fftSize = FFT_SIZE;
    analyser.smoothingTimeConstant = SMOOTHING_TIME_CONSTANT;
    analyser.minDecibels = MINIMUM_DECIBELS;
    analyser.maxDecibels = MAXIMUM_DECIBELS;
    source.connect(analyser);
    void context.resume().catch(() => {});
    const frequencyData = new Float32Array(analyser.frequencyBinCount);
    let previousLevels = silentLevels();
    let frameReference = requestAnimationFrame(function readLevels() {
      analyser.getFloatFrequencyData(frequencyData);
      previousLevels = computeBandLevels(frequencyData, context.sampleRate, previousLevels);
      setLevels(previousLevels);
      frameReference = requestAnimationFrame(readLevels);
    });

    return () => {
      cancelAnimationFrame(frameReference);
      source.disconnect();
      analyser.disconnect();
      void context.close().catch(() => {});
    };
  }, [stream, prefersReducedMotion]);

  return levels;
}
