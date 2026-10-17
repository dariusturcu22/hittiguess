import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { clampVolume, resetAudioSelection, useAudioDevices } from "./use-audio-devices";

function mockMediaDevices() {
  const enumerateDevices = vi.fn(async () => [
    { deviceId: "mic-1", kind: "audioinput", label: "Built-in mic" },
    { deviceId: "mic-2", kind: "audioinput", label: "" },
    { deviceId: "spk-1", kind: "audiooutput", label: "Speakers" },
    { deviceId: "cam-1", kind: "videoinput", label: "Camera" },
  ]);
  Object.defineProperty(window.navigator, "mediaDevices", {
    value: { enumerateDevices },
    configurable: true,
  });
  return enumerateDevices;
}

describe("useAudioDevices", () => {
  beforeEach(() => {
    window.localStorage.clear();
    resetAudioSelection();
  });

  it("lists inputs and outputs with fallback labels", async () => {
    mockMediaDevices();
    window.localStorage.clear();

    const { result } = renderHook(() => useAudioDevices());

    await act(async () => {});
    expect(result.current.microphones).toEqual([
      { deviceId: "mic-1", label: "Built-in mic" },
      { deviceId: "mic-2", label: "Microphone 2" },
    ]);
    expect(result.current.speakers).toEqual([{ deviceId: "spk-1", label: "Speakers" }]);
  });

  it("persists the selected devices across mounts", async () => {
    mockMediaDevices();
    window.localStorage.clear();

    const first = renderHook(() => useAudioDevices());
    await act(async () => {});
    act(() => {
      first.result.current.selectMicrophone("mic-2");
      first.result.current.selectSpeaker("spk-1");
    });
    first.unmount();

    const second = renderHook(() => useAudioDevices());
    await act(async () => {});
    expect(second.result.current.microphoneDeviceId).toBe("mic-2");
    expect(second.result.current.speakerDeviceId).toBe("spk-1");
    second.unmount();
    window.localStorage.clear();
  });

  it("starts both volumes at 100 percent", () => {
    mockMediaDevices();

    const { result } = renderHook(() => useAudioDevices());

    expect(result.current.microphoneVolume).toBe(100);
    expect(result.current.speakerVolume).toBe(100);
  });

  it("keeps the volumes within their ranges and saves them", () => {
    mockMediaDevices();
    const { result } = renderHook(() => useAudioDevices());

    act(() => {
      result.current.setMicrophoneVolume(250);
      result.current.setSpeakerVolume(140);
    });

    expect(result.current.microphoneVolume).toBe(200);
    expect(result.current.speakerVolume).toBe(100);
    expect(window.localStorage.getItem("hittiguess-microphone-volume")).toBe("200");

    act(() => result.current.setSpeakerVolume(35));
    expect(window.localStorage.getItem("hittiguess-speaker-volume")).toBe("35");
  });

  it("applies a change made in one component to every other component at once", () => {
    mockMediaDevices();
    const sidebar = renderHook(() => useAudioDevices());
    const popup = renderHook(() => useAudioDevices());

    act(() => popup.result.current.setSpeakerVolume(40));
    act(() => popup.result.current.selectMicrophone("mic-2"));

    expect(sidebar.result.current.speakerVolume).toBe(40);
    expect(sidebar.result.current.microphoneDeviceId).toBe("mic-2");
  });

  it("restores saved volumes on the next load", () => {
    mockMediaDevices();
    window.localStorage.setItem("hittiguess-speaker-volume", "55");
    resetAudioSelection();

    const { result } = renderHook(() => useAudioDevices());

    expect(result.current.speakerVolume).toBe(55);
  });

  it("starts the noise gate mid range, RNNoise on, and song volume at full", () => {
    mockMediaDevices();

    const { result } = renderHook(() => useAudioDevices());

    expect(result.current.noiseGateSensitivity).toBe(50);
    expect(result.current.isRnnoiseEnabled).toBe(true);
    expect(result.current.songVolume).toBe(100);
  });

  it("saves the noise gate, RNNoise, and song volume and restores them after a store reset", () => {
    mockMediaDevices();
    const { result } = renderHook(() => useAudioDevices());

    act(() => {
      result.current.setNoiseGateSensitivity(72);
      result.current.setRnnoiseEnabled(false);
      result.current.setSongVolume(64);
    });
    expect(window.localStorage.getItem("hittiguess-noise-gate-sensitivity")).toBe("72");
    expect(window.localStorage.getItem("hittiguess-rnnoise-enabled")).toBe("false");
    expect(window.localStorage.getItem("hittiguess-song-volume")).toBe("64");

    act(() => resetAudioSelection());

    expect(result.current.noiseGateSensitivity).toBe(72);
    expect(result.current.isRnnoiseEnabled).toBe(false);
    expect(result.current.songVolume).toBe(64);
  });

  it("clamps out-of-range stored noise gate and song volume values", () => {
    mockMediaDevices();
    window.localStorage.setItem("hittiguess-noise-gate-sensitivity", "150");
    window.localStorage.setItem("hittiguess-song-volume", "250");
    resetAudioSelection();

    const { result } = renderHook(() => useAudioDevices());

    expect(result.current.noiseGateSensitivity).toBe(100);
    expect(result.current.songVolume).toBe(100);
  });

  it("clamps a negative stored noise gate value up to the minimum", () => {
    mockMediaDevices();
    window.localStorage.setItem("hittiguess-noise-gate-sensitivity", "-40");
    resetAudioSelection();

    const { result } = renderHook(() => useAudioDevices());

    expect(result.current.noiseGateSensitivity).toBe(0);
  });

  it("falls back to the RNNoise default when the stored value is corrupt", () => {
    mockMediaDevices();
    window.localStorage.setItem("hittiguess-rnnoise-enabled", "maybe");
    resetAudioSelection();

    const { result } = renderHook(() => useAudioDevices());

    expect(result.current.isRnnoiseEnabled).toBe(true);
  });
});

describe("clampVolume", () => {
  it("rounds, floors at zero, caps at the maximum, and falls back for garbage", () => {
    expect(clampVolume(49.6, 100)).toBe(50);
    expect(clampVolume(-5, 100)).toBe(0);
    expect(clampVolume(300, 200)).toBe(200);
    expect(clampVolume(Number.NaN, 200)).toBe(100);
  });
});
