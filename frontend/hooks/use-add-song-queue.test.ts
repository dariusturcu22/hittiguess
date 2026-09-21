import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { SongDTO } from "@/hooks/models";
import { useAddSongQueue } from "./use-add-song-queue";

function catalogSong(id: number): SongDTO {
  return {
    id,
    artists: [{ name: "Artist" }],
    title: `Song ${id}`,
    releaseYear: 2000,
    youtubeId: `video-${id}`,
    verificationStatus: "VERIFIED",
  } as SongDTO;
}

describe("useAddSongQueue", () => {
  it("toggles songs in and out of the playlist tray", () => {
    const { result } = renderHook(() => useAddSongQueue(101));

    act(() => {
      result.current.toggleQueued(catalogSong(1));
    });
    expect(result.current.queue.map((song) => song.id)).toEqual([1]);

    act(() => {
      result.current.toggleQueued(catalogSong(1));
    });
    expect(result.current.queue).toEqual([]);
  });

  it("keeps each playlist tray separate and clears only on commit", () => {
    const first = renderHook(() => useAddSongQueue(102));
    const second = renderHook(() => useAddSongQueue(103));

    act(() => {
      first.result.current.toggleQueued(catalogSong(1));
      second.result.current.toggleQueued(catalogSong(2));
    });
    expect(first.result.current.queue.map((song) => song.id)).toEqual([1]);
    expect(second.result.current.queue.map((song) => song.id)).toEqual([2]);

    act(() => {
      first.result.current.clearQueue();
    });
    expect(first.result.current.queue).toEqual([]);
    expect(second.result.current.queue.map((song) => song.id)).toEqual([2]);

    act(() => {
      second.result.current.clearQueue();
    });
  });
});
