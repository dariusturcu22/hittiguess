import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AUDIO_TEST_LEVEL_THRESHOLD, AUDIO_TEST_REQUIRED_MILLISECONDS, SILENT_SHARE_TIMEOUT_MILLISECONDS } from "@/lib/audio-check";
import { AUDIO_TEST_SAMPLE_INTERVAL_MILLISECONDS, useAudioShareTest } from "./use-audio-share-test";

const captureState = vi.hoisted(() => ({
  stream: null as MediaStream | null,
  listeners: new Set<() => void>(),
  startResult: { stream: null as MediaStream | null, errorMessage: null as string | null, errorKind: undefined as string | undefined },
}));

function setCapture(stream: MediaStream | null) {
  captureState.stream = stream;
  captureState.listeners.forEach((listener) => listener());
}

vi.mock("@/lib/song-capture", () => ({
  subscribeSongCapture: (listener: () => void) => {
    captureState.listeners.add(listener);
    return () => captureState.listeners.delete(listener);
  },
  getSongCaptureStream: () => captureState.stream,
  startSongCapture: vi.fn(async () => {
    if (captureState.startResult.stream) setCapture(captureState.startResult.stream);
    return captureState.startResult;
  }),
  stopSongCapture: vi.fn(() => setCapture(null)),
}));

// The analyser reports whatever amplitude the test sets, as a constant waveform whose
// root mean square equals that amplitude.
let analyserAmplitude = 0;

class MockAnalyser {
  fftSize = 2048;
  connect = vi.fn();
  disconnect = vi.fn();
  getFloatTimeDomainData(samples: Float32Array) {
    samples.fill(analyserAmplitude);
  }
}

class MockAudioContext {
  createMediaStreamSource = vi.fn(() => ({ connect: vi.fn(), disconnect: vi.fn() }));
  createAnalyser = vi.fn(() => new MockAnalyser());
  resume = vi.fn(async () => {});
  close = vi.fn(async () => {});
}

const LOUD_AMPLITUDE = AUDIO_TEST_LEVEL_THRESHOLD * 10;
const QUIET_AMPLITUDE = AUDIO_TEST_LEVEL_THRESHOLD / 4;
const sharedStream = { id: "shared-screen" } as unknown as MediaStream;

function advance(milliseconds: number) {
  act(() => {
    vi.advanceTimersByTime(milliseconds);
  });
}

describe("useAudioShareTest", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal("AudioContext", MockAudioContext);
    analyserAmplitude = 0;
    captureState.stream = null;
    captureState.listeners.clear();
    captureState.startResult = { stream: sharedStream, errorMessage: null, errorKind: undefined };
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("reports testing for a new share and passes once the level stays up for half a second", async () => {
    const onStatus = vi.fn();
    const { result } = renderHook(() => useAudioShareTest({ isActive: true, onStatus }));

    await act(async () => {
      await result.current.share();
    });
    expect(onStatus).toHaveBeenLastCalledWith("TESTING");

    analyserAmplitude = LOUD_AMPLITUDE;
    advance(AUDIO_TEST_REQUIRED_MILLISECONDS - AUDIO_TEST_SAMPLE_INTERVAL_MILLISECONDS);
    expect(result.current.hasPassed).toBe(false);
    expect(result.current.level).toBeCloseTo(LOUD_AMPLITUDE);

    advance(AUDIO_TEST_SAMPLE_INTERVAL_MILLISECONDS * 2);
    expect(result.current.hasPassed).toBe(true);
    expect(onStatus).toHaveBeenLastCalledWith("OK");
  });

  it("does not pass while the shared audio stays below the threshold, and reports a silent share", async () => {
    const onStatus = vi.fn();
    const { result } = renderHook(() => useAudioShareTest({ isActive: true, onStatus }));
    await act(async () => {
      await result.current.share();
    });

    analyserAmplitude = QUIET_AMPLITUDE;
    advance(SILENT_SHARE_TIMEOUT_MILLISECONDS);

    expect(result.current.hasPassed).toBe(false);
    expect(result.current.isSilent).toBe(true);
    expect(onStatus).toHaveBeenLastCalledWith("FAILED");
  });

  it("starts the half second over after a dip", async () => {
    const onStatus = vi.fn();
    const { result } = renderHook(() => useAudioShareTest({ isActive: true, onStatus }));
    await act(async () => {
      await result.current.share();
    });

    analyserAmplitude = LOUD_AMPLITUDE;
    advance(AUDIO_TEST_REQUIRED_MILLISECONDS / 2);
    analyserAmplitude = 0;
    advance(AUDIO_TEST_SAMPLE_INTERVAL_MILLISECONDS);
    analyserAmplitude = LOUD_AMPLITUDE;
    advance(AUDIO_TEST_REQUIRED_MILLISECONDS / 2);

    expect(result.current.hasPassed).toBe(false);
  });

  it("fails at once for a share without an audio track", async () => {
    captureState.startResult = { stream: null, errorMessage: "Chrome shared no audio.", errorKind: "no-audio-track" };
    const onStatus = vi.fn();
    const { result } = renderHook(() => useAudioShareTest({ isActive: true, onStatus }));

    await act(async () => {
      await result.current.share();
    });

    expect(onStatus).toHaveBeenCalledExactlyOnceWith("FAILED");
    expect(result.current.isCapturing).toBe(false);
    expect(result.current.shareErrorKind).toBe("no-audio-track");
  });

  it("leaves a cancelled picker unreported", async () => {
    captureState.startResult = { stream: null, errorMessage: "The audio share was cancelled. Try again.", errorKind: "cancelled" };
    const onStatus = vi.fn();
    const { result } = renderHook(() => useAudioShareTest({ isActive: true, onStatus }));

    await act(async () => {
      await result.current.share();
    });

    expect(onStatus).not.toHaveBeenCalled();
    expect(result.current.shareErrorMessage).toContain("cancelled");
  });

  it("reports a share that stops", async () => {
    const onStatus = vi.fn();
    const { result } = renderHook(() => useAudioShareTest({ isActive: true, onStatus }));
    await act(async () => {
      await result.current.share();
    });

    act(() => setCapture(null));

    expect(onStatus).toHaveBeenLastCalledWith("NOT_SHARED");
  });

  it("does not test again a share the server already holds as passed", () => {
    captureState.stream = sharedStream;
    const onStatus = vi.fn();
    const { result } = renderHook(() => useAudioShareTest({ isActive: true, isAlreadyPassed: true, onStatus }));

    advance(SILENT_SHARE_TIMEOUT_MILLISECONDS);

    expect(result.current.hasPassed).toBe(true);
    expect(onStatus).not.toHaveBeenCalled();
  });

  it("reports nothing while inactive", async () => {
    const onStatus = vi.fn();
    const { result } = renderHook(() => useAudioShareTest({ isActive: false, onStatus }));
    await act(async () => {
      await result.current.share();
    });
    analyserAmplitude = LOUD_AMPLITUDE;
    advance(SILENT_SHARE_TIMEOUT_MILLISECONDS);

    expect(onStatus).not.toHaveBeenCalled();
  });
});
