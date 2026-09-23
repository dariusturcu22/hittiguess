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

// Whether the DJ's YouTube tab audio is currently shared into the voice mesh, read by
// the session view to switch its share action between "share" and "sharing".
let isTabAudioShared = false;
const tabAudioListeners = new Set<() => void>();

export function publishTabAudioSharing(isShared: boolean) {
  isTabAudioShared = isShared;
  tabAudioListeners.forEach((listener) => listener());
}

function subscribeTabAudioSharing(listener: () => void): () => void {
  tabAudioListeners.add(listener);
  return () => {
    tabAudioListeners.delete(listener);
  };
}

export function useIsTabAudioShared(): boolean {
  return useSyncExternalStore(subscribeTabAudioSharing, () => isTabAudioShared, () => false);
}

export const DJ_AUDIO_SHARE_EVENT = "session-start-audio-share";

// Called synchronously from the DJ's share click so the voice sidebar's capture request
// still runs inside that click's user activation.
export function requestDjTabAudioShare() {
  window.dispatchEvent(new CustomEvent(DJ_AUDIO_SHARE_EVENT));
}
