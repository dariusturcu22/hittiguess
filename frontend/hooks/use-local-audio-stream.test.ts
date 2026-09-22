import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { publishLocalAudioStream, useLocalAudioStream } from "./use-local-audio-stream";

describe("useLocalAudioStream", () => {
  it("publishes the shared stream to every subscriber", () => {
    const first = renderHook(() => useLocalAudioStream());
    const second = renderHook(() => useLocalAudioStream());
    const stream = { id: "local-stream" } as unknown as MediaStream;

    expect(first.result.current).toBeNull();

    act(() => {
      publishLocalAudioStream(stream);
    });

    expect(first.result.current).toBe(stream);
    expect(second.result.current).toBe(stream);

    act(() => {
      publishLocalAudioStream(null);
    });

    expect(first.result.current).toBeNull();
    expect(second.result.current).toBeNull();
  });
});
