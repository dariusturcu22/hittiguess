"use client";

import { useSyncExternalStore } from "react";

import type { SongDTO } from "@/hooks/models";

// The add tray survives leaving the add screen: queues live outside React in
// this module, keyed by destination playlist, so navigating away and back (or
// queueing for another playlist in between) never loses uncommitted picks.
// Committing clears only the submitted playlist's queue.
const queuesByPlaylistId = new Map<number, SongDTO[]>();
const EMPTY_QUEUE: SongDTO[] = [];

const listeners = new Set<() => void>();

function emitChange() {
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function queueFor(playlistId: number): SongDTO[] {
  return queuesByPlaylistId.get(playlistId) ?? EMPTY_QUEUE;
}

export function useAddSongQueue(playlistId: number) {
  const queue = useSyncExternalStore(subscribe, () => queueFor(playlistId));

  function toggleQueued(song: SongDTO) {
    const currentQueue = queueFor(playlistId);
    queuesByPlaylistId.set(
      playlistId,
      currentQueue.some((queued) => queued.id === song.id)
        ? currentQueue.filter((queued) => queued.id !== song.id)
        : [...currentQueue, song],
    );
    emitChange();
  }

  function clearQueue() {
    if (queuesByPlaylistId.delete(playlistId)) {
      emitChange();
    }
  }

  return { queue, toggleQueued, clearQueue };
}
