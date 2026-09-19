import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { useVoiceMesh } from "./use-voice-mesh";

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  useGetVoiceTurnCredentials: () => ({ data: undefined }),
}));

vi.mock("@stomp/stompjs", () => ({
  Client: class {
    activate() {}
    deactivate() { return Promise.resolve(); }
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
  it("keeps the selected tab audio and stops its display video track", async () => {
    const audioTrack = { kind: "audio", stop: vi.fn() } as unknown as MediaStreamTrack;
    const videoTrack = { kind: "video", stop: vi.fn() } as unknown as MediaStreamTrack;
    const displayStream = new TestMediaStream([audioTrack, videoTrack]) as unknown as MediaStream;
    const getDisplayMedia = vi.fn().mockResolvedValue(displayStream);
    Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: { getDisplayMedia } });
    vi.stubGlobal("MediaStream", TestMediaStream);

    const { result } = renderHook(() => useVoiceMesh(1, 1, [], false));

    await act(async () => {
      expect(await result.current.startTabAudio()).toBe(true);
    });

    expect(getDisplayMedia).toHaveBeenCalledWith({ video: true, audio: true });
    expect(videoTrack.stop).toHaveBeenCalledOnce();
    expect(audioTrack.stop).not.toHaveBeenCalled();
  });
});
