import { describe, expect, it } from "vitest";

import { audioShareWarningKind } from "./audio-share-warning";

describe("audioShareWarningKind", () => {
  it("warns a player whose browser cannot share, as a spectator", () => {
    expect(audioShareWarningKind({ canShareAudio: false, isDj: false, djCanShareAudio: true })).toBe("own-browser");
  });

  it("warns a DJ who cannot share that the group will not hear the song", () => {
    expect(audioShareWarningKind({ canShareAudio: false, isDj: true, djCanShareAudio: false })).toBe("own-browser-is-dj");
  });

  it("warns listeners when the DJ cannot share", () => {
    expect(audioShareWarningKind({ canShareAudio: true, isDj: false, djCanShareAudio: false })).toBe("dj-cannot-share");
  });

  it("stays quiet when the browser can share and the DJ can too or is unknown", () => {
    expect(audioShareWarningKind({ canShareAudio: true, isDj: false, djCanShareAudio: true })).toBeNull();
    expect(audioShareWarningKind({ canShareAudio: true, isDj: false, djCanShareAudio: undefined })).toBeNull();
    expect(audioShareWarningKind({ canShareAudio: true, isDj: true, djCanShareAudio: true })).toBeNull();
  });
});
