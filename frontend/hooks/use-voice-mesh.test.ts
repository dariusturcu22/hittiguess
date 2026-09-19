import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { shouldCutoffAudioStream, useVoiceMesh } from "./use-voice-mesh";

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  useGetVoiceTurnCredentials: () => ({ data: undefined }),
}));

vi.mock("@stomp/stompjs", () => ({
  Client: class {
    activate() {}
    deactivate() { return Promise.resolve(); }
    subscribe() {}
  },
}));

class TestMediaStream {
  constructor(private readonly tracks: MediaStreamTrack[]) {}

  getTracks() { return this.tracks; }
  getAudioTracks() { return this.tracks.filter((track) => track.kind === "audio"); }
  getVideoTracks() { return this.tracks.filter((track) => track.kind === "video"); }
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("useVoiceMesh", () => {
  it("cuts off only the active player's stream on guess lock-in", () => {
    const lockedEvent = { type: "GUESS_LOCKED", payload: { activePlayerId: 7 } };

    expect(shouldCutoffAudioStream(lockedEvent, 7)).toBe(true);
    expect(shouldCutoffAudioStream(lockedEvent, 8)).toBe(false);
    expect(shouldCutoffAudioStream({ type: "ROUND_SCORED", payload: { activePlayerId: 7 } }, 7)).toBe(false);
  });

  it("keeps the selected tab audio and stops its display video track", async () => {
    let endedListener: (() => void) | undefined;
    const audioTrack = {
      kind: "audio",
      stop: vi.fn(),
      addEventListener: (eventName: string, listener: () => void) => {
        if (eventName === "ended") endedListener = listener;
      },
    } as unknown as MediaStreamTrack;
    const videoTrack = { kind: "video", stop: vi.fn() } as unknown as MediaStreamTrack;
    const microphoneTrack = { kind: "audio", stop: vi.fn() } as unknown as MediaStreamTrack;
    const displayStream = new TestMediaStream([audioTrack, videoTrack]) as unknown as MediaStream;
    const microphoneStream = new TestMediaStream([microphoneTrack]) as unknown as MediaStream;
    const getDisplayMedia = vi.fn().mockResolvedValue(displayStream);
    const getUserMedia = vi.fn().mockResolvedValue(microphoneStream);
    Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: { getDisplayMedia, getUserMedia } });
    vi.stubGlobal("MediaStream", TestMediaStream);

    const { result } = renderHook(() => useVoiceMesh(1, 1, [], false));

    await act(async () => {
      expect(await result.current.startTabAudio()).toBe(true);
    });

    expect(getDisplayMedia).toHaveBeenCalledWith({ video: true, audio: true });
    expect(videoTrack.stop).toHaveBeenCalledOnce();
    expect(audioTrack.stop).not.toHaveBeenCalled();

    await act(async () => {
      endedListener?.();
    });

    expect(getUserMedia).toHaveBeenCalledWith({ audio: true });
  });
});
