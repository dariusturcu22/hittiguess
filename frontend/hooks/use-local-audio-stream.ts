"use client";

import { useSyncExternalStore } from "react";

// The currently shared local audio (microphone or DJ tab capture), published
// by the voice mesh and read by the visualizer. Module-level because the mesh
// lives in the sidebar while the card lives in the session view, with the app
// shell between them and no shared props.
let currentStream: MediaStream | null = null;
const listeners = new Set<() => void>();

function emitStream() {
  listeners.forEach((listener) => listener());
}

export function publishLocalAudioStream(stream: MediaStream | null) {
  currentStream = stream;
  emitStream();
}

function subscribeStream(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function snapshotStream(): MediaStream | null {
  return currentStream;
}

function snapshotStreamServer(): MediaStream | null {
  return null;
}

export function useLocalAudioStream(): MediaStream | null {
  return useSyncExternalStore(subscribeStream, snapshotStream, snapshotStreamServer);
}
