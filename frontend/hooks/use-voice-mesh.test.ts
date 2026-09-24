import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { shouldSilenceDjForActivePlayer, useVoiceMesh } from "./use-voice-mesh";

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

  it("reports an insecure context instead of a permission denial", async () => {
    Object.defineProperty(window.navigator, "mediaDevices", {
      value: undefined,
      configurable: true,
    });

    const { result } = renderHook(() => useVoiceMesh(4, 11, [], false, {}));

    let opened = true;
    await act(async () => {
      opened = await result.current.startMicrophone();
    });

    expect(opened).toBe(false);
    expect(result.current.microphoneError).toBe(true);
    expect(result.current.microphoneErrorMessage).toBe("Voice chat needs HTTPS or localhost to use a microphone. You can still listen.");
  });

  it("names permission denial and missing devices distinctly", async () => {
    const failingMediaDevices = { getUserMedia: vi.fn() };
    Object.defineProperty(window.navigator, "mediaDevices", {
      value: failingMediaDevices,
      configurable: true,
    });

    const { result } = renderHook(() => useVoiceMesh(4, 11, [], false, {}));

    failingMediaDevices.getUserMedia.mockRejectedValueOnce(new DOMException("denied", "NotAllowedError"));
    await act(async () => {
      await result.current.startMicrophone();
    });
    expect(result.current.microphoneErrorMessage).toBe("Microphone permission is needed to talk. You can still listen.");

    failingMediaDevices.getUserMedia.mockRejectedValueOnce(new DOMException("none", "NotFoundError"));
    await act(async () => {
      await result.current.startMicrophone();
    });
    expect(result.current.microphoneErrorMessage).toBe("No microphone was found. You can still listen.");
  });
});

describe("shouldSilenceDjForActivePlayer", () => {
  const ACTIVE_PLAYER_ID = 3;

  it("silences the DJ for the active player from lock-in until the next round", () => {
    for (const status of ["COUNTDOWN", "BETTING", "REVEALED", "SCORED"]) {
      expect(shouldSilenceDjForActivePlayer({ activePlayerId: ACTIVE_PLAYER_ID, status }, ACTIVE_PLAYER_ID)).toBe(true);
    }
  });

  it("keeps the song audible while the active player is still placing", () => {
    expect(shouldSilenceDjForActivePlayer({ activePlayerId: ACTIVE_PLAYER_ID, status: "AWAITING_PLACEMENT" }, ACTIVE_PLAYER_ID)).toBe(false);
  });

  it("never silences the DJ for anyone other than the active player", () => {
    expect(shouldSilenceDjForActivePlayer({ activePlayerId: ACTIVE_PLAYER_ID, status: "BETTING" }, ACTIVE_PLAYER_ID + 1)).toBe(false);
  });
});

describe("useVoiceMesh tab audio", () => {
  it("reports when the browser can't capture tab audio", async () => {
    Object.defineProperty(window.navigator, "mediaDevices", {
      value: { getUserMedia },
      configurable: true,
    });
    const { result } = renderHook(() => useVoiceMesh(4, 11, [], false, {}));

    let shared = true;
    await act(async () => {
      shared = await result.current.startTabAudio();
    });

    expect(shared).toBe(false);
    expect(result.current.tabAudioErrorMessage).toBe("This browser can't share tab audio.");
  });

  it("asks for display media with audio straight away, excluding the game tab", async () => {
    const getDisplayMedia = vi.fn(async () => ({
      getAudioTracks: () => [],
      getVideoTracks: () => [],
      getTracks: () => [],
    }));
    Object.defineProperty(window.navigator, "mediaDevices", {
      value: { getUserMedia, getDisplayMedia },
      configurable: true,
    });
    const { result } = renderHook(() => useVoiceMesh(4, 11, [], false, {}));

    let shared = true;
    await act(async () => {
      shared = await result.current.startTabAudio();
    });

    expect(getDisplayMedia).toHaveBeenCalledWith(expect.objectContaining({ audio: true, selfBrowserSurface: "exclude" }));
    expect(shared).toBe(false);
    expect(result.current.tabAudioErrorMessage).toBe("No audio was shared. Pick the YouTube tab and turn on tab audio.");
  });
});
