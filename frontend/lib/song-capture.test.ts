import { afterEach, describe, expect, it, vi } from "vitest";

import { startSongCapture, stopSongCapture } from "./song-capture";

vi.mock("@/lib/audio-share-outcome", () => ({ recordAudioShareOutcome: vi.fn() }));

class FakeMediaStream {
  constructor(private readonly tracks: unknown[]) {}
  getTracks() {
    return this.tracks;
  }
}

function stubDisplayMedia() {
  const audioTrack = { addEventListener: vi.fn(), removeEventListener: vi.fn(), stop: vi.fn(), contentHint: "" };
  const displayStream = {
    getVideoTracks: () => [],
    getAudioTracks: () => [audioTrack],
    getTracks: () => [audioTrack],
  };
  const getDisplayMedia = vi.fn(async () => displayStream);
  Object.defineProperty(window.navigator, "mediaDevices", { value: { getDisplayMedia }, configurable: true });
  vi.stubGlobal("MediaStream", FakeMediaStream);
  return getDisplayMedia;
}

afterEach(() => {
  stopSongCapture();
  vi.unstubAllGlobals();
});

describe("startSongCapture", () => {
  it("asks the browser to keep the game tab's own output, the other players' voices, out of the shared audio", async () => {
    const getDisplayMedia = stubDisplayMedia();

    await startSongCapture();

    expect(getDisplayMedia).toHaveBeenCalledWith(
      expect.objectContaining({ audio: expect.objectContaining({ restrictOwnAudio: true, echoCancellation: false }) }),
    );
  });
});
