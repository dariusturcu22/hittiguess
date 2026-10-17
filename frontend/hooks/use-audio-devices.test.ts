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
});

describe("clampVolume", () => {
  it("rounds, floors at zero, caps at the maximum, and falls back for garbage", () => {
    expect(clampVolume(49.6, 100)).toBe(50);
    expect(clampVolume(-5, 100)).toBe(0);
    expect(clampVolume(300, 200)).toBe(200);
    expect(clampVolume(Number.NaN, 200)).toBe(100);
  });
});
