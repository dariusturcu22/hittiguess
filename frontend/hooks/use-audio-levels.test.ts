import { renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { SILENCE_FLOOR_LEVEL, VISUALIZER_BAND_COUNT, bandFrequencyEdges, computeBandLevels, useAudioLevels } from "./use-audio-levels";

const SAMPLE_RATE_HERTZ = 44100;
const FFT_SIZE = 2048;
const FREQUENCY_BIN_COUNT = FFT_SIZE / 2;
const SILENT_DECIBELS = Number.NEGATIVE_INFINITY;
const QUIET_DECIBELS = -80;
const FLAT_SPECTRUM_DECIBELS = -60;
const LOUD_DECIBELS = -10;
const FULL_LEVEL = 1;
const LOWEST_BAND_INDEX = 0;
const HIGHEST_BAND_INDEX = VISUALIZER_BAND_COUNT - 1;
const SETTLE_FRAME_COUNT = 200;
const MAJORITY_PROGRESS = 0.5;
const MINOR_PROGRESS_MAXIMUM = 0.25;
const RATIO_PRECISION = 5;

function flatSpectrum(decibels: number): Float32Array {
  return new Float32Array(FREQUENCY_BIN_COUNT).fill(decibels);
}

function silenceLevels(): number[] {
  return new Array(VISUALIZER_BAND_COUNT).fill(SILENCE_FLOOR_LEVEL);
}

function fullLevels(): number[] {
  return new Array(VISUALIZER_BAND_COUNT).fill(FULL_LEVEL);
}

function frequencyBinIndex(frequencyHertz: number): number {
  const binWidthHertz = SAMPLE_RATE_HERTZ / 2 / FREQUENCY_BIN_COUNT;
  return Math.round(frequencyHertz / binWidthHertz);
}

function spectrumWithBand(spectrum: Float32Array, lowEdgeHertz: number, highEdgeHertz: number): Float32Array {
  const firstBinIndex = frequencyBinIndex(lowEdgeHertz);
  const lastBinIndex = frequencyBinIndex(highEdgeHertz) - 1;
  for (let binIndex = firstBinIndex; binIndex <= lastBinIndex; binIndex += 1) {
    spectrum[binIndex] = LOUD_DECIBELS;
  }
  return spectrum;
}

function settleLevels(spectrum: Float32Array, initialLevels: number[]): number[] {
  let levels = initialLevels;
  for (let frameIndex = 0; frameIndex < SETTLE_FRAME_COUNT; frameIndex += 1) {
    levels = computeBandLevels(spectrum, SAMPLE_RATE_HERTZ, levels);
  }
  return levels;
}

describe("bandFrequencyEdges", () => {
  it("uses five bands from 40 Hz to 16 kHz", () => {
    const EXPECTED_BAND_COUNT = 5;
    const LOWEST_BAND_FREQUENCY_HZ = 40;
    const HIGHEST_BAND_FREQUENCY_HZ = 16000;
    const edges = bandFrequencyEdges(SAMPLE_RATE_HERTZ);

    expect(VISUALIZER_BAND_COUNT).toBe(EXPECTED_BAND_COUNT);
    expect(edges[LOWEST_BAND_INDEX]).toBeCloseTo(LOWEST_BAND_FREQUENCY_HZ, RATIO_PRECISION);
    expect(edges.at(-1)).toBeCloseTo(HIGHEST_BAND_FREQUENCY_HZ, RATIO_PRECISION);
    expect(computeBandLevels(flatSpectrum(FLAT_SPECTRUM_DECIBELS), SAMPLE_RATE_HERTZ, [])).toHaveLength(EXPECTED_BAND_COUNT);
  });

  it("spaces the bands logarithmically, so low bands cover narrower ranges", () => {
    const edges = bandFrequencyEdges(SAMPLE_RATE_HERTZ);
    expect(edges).toHaveLength(VISUALIZER_BAND_COUNT + 1);

    const bandWidths = edges.slice(1).map((highEdgeHertz, edgeIndex) => highEdgeHertz - edges[edgeIndex]);
    bandWidths.forEach((bandWidth, bandIndex) => {
      if (bandIndex === LOWEST_BAND_INDEX) return;
      expect(bandWidth).toBeGreaterThan(bandWidths[bandIndex - 1]);
    });

    const edgeRatios = edges.slice(1).map((highEdgeHertz, edgeIndex) => highEdgeHertz / edges[edgeIndex]);
    edgeRatios.forEach((edgeRatio) => expect(edgeRatio).toBeCloseTo(edgeRatios[LOWEST_BAND_INDEX], RATIO_PRECISION));
  });
});

describe("computeBandLevels", () => {
  it("returns one bounded level per band", () => {
    const levels = computeBandLevels(flatSpectrum(FLAT_SPECTRUM_DECIBELS), SAMPLE_RATE_HERTZ, []);

    expect(levels).toHaveLength(VISUALIZER_BAND_COUNT);
    levels.forEach((level) => {
      expect(level).toBeGreaterThanOrEqual(SILENCE_FLOOR_LEVEL);
      expect(level).toBeLessThanOrEqual(FULL_LEVEL);
    });
  });

  it("tilts a flat spectrum so the high bands stay visible", () => {
    const levels = settleLevels(flatSpectrum(FLAT_SPECTRUM_DECIBELS), silenceLevels());

    expect(levels[LOWEST_BAND_INDEX]).toBeGreaterThan(0.1);
    expect(levels[HIGHEST_BAND_INDEX]).toBeGreaterThan(0.5);
    expect(levels[HIGHEST_BAND_INDEX]).toBeGreaterThan(levels[LOWEST_BAND_INDEX]);
  });

  it("rises toward a louder target faster than it falls toward a quieter one", () => {
    const loudSpectrum = flatSpectrum(LOUD_DECIBELS);
    const quietSpectrum = flatSpectrum(QUIET_DECIBELS);
    const risingTarget = settleLevels(loudSpectrum, silenceLevels())[HIGHEST_BAND_INDEX];
    const fallingTarget = settleLevels(quietSpectrum, fullLevels())[HIGHEST_BAND_INDEX];
    const risingStep = computeBandLevels(loudSpectrum, SAMPLE_RATE_HERTZ, silenceLevels())[HIGHEST_BAND_INDEX];
    const fallingStep = computeBandLevels(quietSpectrum, SAMPLE_RATE_HERTZ, fullLevels())[HIGHEST_BAND_INDEX];

    const risingProgress = (risingStep - SILENCE_FLOOR_LEVEL) / (risingTarget - SILENCE_FLOOR_LEVEL);
    const fallingProgress = (FULL_LEVEL - fallingStep) / (FULL_LEVEL - fallingTarget);

    expect(risingProgress).toBeGreaterThan(MAJORITY_PROGRESS);
    expect(fallingProgress).toBeLessThan(MINOR_PROGRESS_MAXIMUM);
    expect(risingProgress).toBeGreaterThan(fallingProgress);
  });

  it("rests at the silence floor when the spectrum is silent", () => {
    const levels = computeBandLevels(flatSpectrum(SILENT_DECIBELS), SAMPLE_RATE_HERTZ, fullLevels());

    expect(levels).toEqual(silenceLevels());
  });

  it("maps the lowest band to the low frequencies and the highest band to the top", () => {
    const edges = bandFrequencyEdges(SAMPLE_RATE_HERTZ);
    const lowestBandLevels = computeBandLevels(
      spectrumWithBand(flatSpectrum(SILENT_DECIBELS), edges[LOWEST_BAND_INDEX], edges[LOWEST_BAND_INDEX + 1]),
      SAMPLE_RATE_HERTZ,
      silenceLevels(),
    );
    const highestBandLevels = computeBandLevels(
      spectrumWithBand(flatSpectrum(SILENT_DECIBELS), edges[HIGHEST_BAND_INDEX], edges[HIGHEST_BAND_INDEX + 1]),
      SAMPLE_RATE_HERTZ,
      silenceLevels(),
    );

    expect(lowestBandLevels[LOWEST_BAND_INDEX]).toBeGreaterThan(SILENCE_FLOOR_LEVEL);
    expect(lowestBandLevels[HIGHEST_BAND_INDEX]).toBe(SILENCE_FLOOR_LEVEL);
    expect(highestBandLevels[HIGHEST_BAND_INDEX]).toBeGreaterThan(SILENCE_FLOOR_LEVEL);
    expect(highestBandLevels[LOWEST_BAND_INDEX]).toBe(SILENCE_FLOOR_LEVEL);
  });
});

describe("useAudioLevels wiring", () => {
  it("feeds only the given stream into the analyser and never to the speaker output", () => {
    const speakerOutput = { speakerOutput: true };
    const analyser = { fftSize: 0, smoothingTimeConstant: 0, minDecibels: 0, maxDecibels: 0, frequencyBinCount: 4, getFloatFrequencyData: vi.fn(), disconnect: vi.fn() };
    const source = { connect: vi.fn(), disconnect: vi.fn() };
    const createMediaStreamSource = vi.fn(() => source);
    class FakeAudioContext {
      destination = speakerOutput;
      sampleRate = SAMPLE_RATE_HERTZ;
      createMediaStreamSource = createMediaStreamSource;
      createAnalyser = () => analyser;
      resume = () => Promise.resolve();
      close = () => Promise.resolve();
    }
    vi.stubGlobal("AudioContext", FakeAudioContext);
    const songStream = {} as MediaStream;

    renderHook(() => useAudioLevels(songStream));

    expect(createMediaStreamSource).toHaveBeenCalledTimes(1);
    expect(createMediaStreamSource).toHaveBeenCalledWith(songStream);
    expect(source.connect).toHaveBeenCalledTimes(1);
    expect(source.connect).toHaveBeenCalledWith(analyser);
    expect(source.connect).not.toHaveBeenCalledWith(speakerOutput);
    vi.unstubAllGlobals();
  });
});
