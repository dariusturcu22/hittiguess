import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { useVoiceMesh } from "./use-voice-mesh";

const getUserMedia = vi.fn(async () => ({ getAudioTracks: () => [{ enabled: true }] }));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  useGetVoiceTurnCredentials: () => ({ data: undefined }),
}));

vi.mock("@stomp/stompjs", () => ({
  Client: vi.fn(() => ({ activate: vi.fn(), deactivate: vi.fn() })),
}));

describe("useVoiceMesh device selection", () => {
  it("opens the selected microphone with an exact device constraint", async () => {
    Object.defineProperty(window.navigator, "mediaDevices", {
      value: { getUserMedia },
      configurable: true,
    });
    getUserMedia.mockClear();

    const { result } = renderHook(() =>
      useVoiceMesh(4, 11, [], false, { microphoneDeviceId: "mic-9" }),
    );

    let opened = false;
    await act(async () => {
      opened = await result.current.startMicrophone();
    });

    expect(opened).toBe(true);
    expect(getUserMedia).toHaveBeenCalledWith({
      audio: { deviceId: { exact: "mic-9" } },
    });
  });

  it("opens the default microphone without a selection", async () => {
    Object.defineProperty(window.navigator, "mediaDevices", {
      value: { getUserMedia },
      configurable: true,
    });
    getUserMedia.mockClear();

    const { result } = renderHook(() => useVoiceMesh(4, 11, [], false, {}));

    await act(async () => {
      await result.current.startMicrophone();
    });

    expect(getUserMedia).toHaveBeenCalledWith({ audio: true });
  });
});
