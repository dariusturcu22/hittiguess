import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { useAudioDevices } from "./use-audio-devices";

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
});
