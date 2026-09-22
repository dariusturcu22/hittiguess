import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { publishLocalAudioStream } from "./use-local-audio-stream";
import { useAudioLevels } from "./use-audio-levels";

const FREQUENCY_BIN_COUNT = 32;

function mockAudioStack(frequencyValue: number) {
  const bins = new Uint8Array(FREQUENCY_BIN_COUNT).fill(frequencyValue);
  const getByteFrequencyData = vi.fn((target: Uint8Array) => {
    target.set(bins);
  });
  const analyser = { fftSize: 0, frequencyBinCount: FREQUENCY_BIN_COUNT, getByteFrequencyData };
  const source = { connect: vi.fn() };
  const context = {
    createMediaStreamSource: vi.fn(() => source),
    createAnalyser: vi.fn(() => analyser),
    close: vi.fn(async () => {}),
  };
  function AudioContextStub(this: unknown) {
    return context;
  }
  vi.stubGlobal("AudioContext", AudioContextStub);
  let frameCallback: FrameRequestCallback = () => {};
  vi.stubGlobal("requestAnimationFrame", vi.fn((callback: FrameRequestCallback) => {
    frameCallback = callback;
    return 1;
  }));
  vi.stubGlobal("cancelAnimationFrame", vi.fn());
  return { context, runFrame: () => act(() => frameCallback(0)) };
}

describe("useAudioLevels", () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    act(() => {
      publishLocalAudioStream(null);
    });
  });

  it("stays silent without a shared stream", () => {
    const { result } = renderHook(() => useAudioLevels());

    expect(result.current).toEqual([0, 0, 0, 0, 0]);
  });

  it("folds analyser bins into per-bar levels", () => {
    mockAudioStack(255);
    const stream = { id: "tab-audio" } as unknown as MediaStream;
    const { result } = renderHook(() => useAudioLevels());

    act(() => {
      publishLocalAudioStream(stream);
    });

    expect(result.current).toEqual([1, 1, 1, 1, 1]);
  });
});
