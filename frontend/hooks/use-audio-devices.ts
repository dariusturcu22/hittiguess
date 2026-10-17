"use client";

import { useCallback, useEffect, useSyncExternalStore } from "react";

export interface AudioDeviceChoice {
  deviceId: string;
  label: string;
}

interface DeviceLists {
  microphones: AudioDeviceChoice[];
  speakers: AudioDeviceChoice[];
}

const MICROPHONE_STORAGE_KEY = "hittiguess-microphone-device-id";
const SPEAKER_STORAGE_KEY = "hittiguess-speaker-device-id";
const MICROPHONE_VOLUME_STORAGE_KEY = "hittiguess-microphone-volume";
const SPEAKER_VOLUME_STORAGE_KEY = "hittiguess-speaker-volume";
const EMPTY_LISTS: DeviceLists = { microphones: [], speakers: [] };

// Percentages. The microphone can be boosted to double for a quiet device; the speaker
// cannot exceed the system volume, which is what an audio element's volume tops out at.
export const DEFAULT_VOLUME_PERCENT = 100;
export const MAX_MICROPHONE_VOLUME_PERCENT = 200;
export const MAX_SPEAKER_VOLUME_PERCENT = 100;

export interface AudioSelection {
  microphoneDeviceId: string | undefined;
  speakerDeviceId: string | undefined;
  microphoneVolume: number;
  speakerVolume: number;
}

const SERVER_SELECTION: AudioSelection = {
  microphoneDeviceId: undefined,
  speakerDeviceId: undefined,
  microphoneVolume: DEFAULT_VOLUME_PERCENT,
  speakerVolume: DEFAULT_VOLUME_PERCENT,
};

let cachedLists: DeviceLists = EMPTY_LISTS;
const listListeners = new Set<() => void>();

function emitLists() {
  listListeners.forEach((listener) => listener());
}

function describeDevices(devices: MediaDeviceInfo[], kind: "audioinput" | "audiooutput", fallback: string): AudioDeviceChoice[] {
  return devices
    .filter((device) => device.kind === kind)
    .map((device, index) => ({
      deviceId: device.deviceId,
      label: device.label || `${fallback} ${index + 1}`,
    }));
}

async function loadDeviceLists() {
  if (!navigator.mediaDevices?.enumerateDevices) {
    return;
  }
  try {
    const devices = await navigator.mediaDevices.enumerateDevices();
    cachedLists = {
      microphones: describeDevices(devices, "audioinput", "Microphone"),
      speakers: describeDevices(devices, "audiooutput", "Speaker"),
    };
    emitLists();
  } catch {
    // Unavailable devices simply leave the lists empty; selection stays manual.
  }
}

function subscribeLists(listener: () => void): () => void {
  listListeners.add(listener);
  void loadDeviceLists();
  return () => {
    listListeners.delete(listener);
  };
}

function snapshotLists(): DeviceLists {
  return cachedLists;
}

function snapshotListsServer(): DeviceLists {
  return EMPTY_LISTS;
}

function storedDeviceId(key: string): string | undefined {
  if (typeof window === "undefined") {
    return undefined;
  }
  return window.localStorage.getItem(key) ?? undefined;
}

export function clampVolume(value: number, maximum: number): number {
  if (!Number.isFinite(value)) return DEFAULT_VOLUME_PERCENT;
  return Math.min(maximum, Math.max(0, Math.round(value)));
}

function storedVolume(key: string, maximum: number): number {
  const stored = typeof window === "undefined" ? null : window.localStorage.getItem(key);
  return stored === null ? DEFAULT_VOLUME_PERCENT : clampVolume(Number(stored), maximum);
}

function persistValue(key: string, value: string | undefined) {
  if (typeof window === "undefined") {
    return;
  }
  if (value === undefined) {
    window.localStorage.removeItem(key);
  } else {
    window.localStorage.setItem(key, value);
  }
}

// The chosen devices and volumes are shared by the voice sidebar, which opens the audio,
// and the settings popup, which changes them, so one module-level store feeds both and a
// change applies to the running call at once. The first read loads what was saved.
let currentSelection: AudioSelection | null = null;
const selectionListeners = new Set<() => void>();

function readSelection(): AudioSelection {
  currentSelection ??= {
    microphoneDeviceId: storedDeviceId(MICROPHONE_STORAGE_KEY),
    speakerDeviceId: storedDeviceId(SPEAKER_STORAGE_KEY),
    microphoneVolume: storedVolume(MICROPHONE_VOLUME_STORAGE_KEY, MAX_MICROPHONE_VOLUME_PERCENT),
    speakerVolume: storedVolume(SPEAKER_VOLUME_STORAGE_KEY, MAX_SPEAKER_VOLUME_PERCENT),
  };
  return currentSelection;
}

function updateSelection(change: Partial<AudioSelection>, storageKey: string, stored: string | undefined) {
  currentSelection = { ...readSelection(), ...change };
  persistValue(storageKey, stored);
  selectionListeners.forEach((listener) => listener());
}

function subscribeSelection(listener: () => void): () => void {
  selectionListeners.add(listener);
  return () => {
    selectionListeners.delete(listener);
  };
}

// Drops the in-memory copy so the next read loads from storage again; tests use it
// between cases.
export function resetAudioSelection() {
  currentSelection = null;
  selectionListeners.forEach((listener) => listener());
}

// Lists the browser's audio inputs and outputs for the voice settings popup.
// Device labels stay empty until microphone permission is granted; selection
// persists in local storage so it survives reloads.
export function useAudioDevices() {
  const { microphones, speakers } = useSyncExternalStore(subscribeLists, snapshotLists, snapshotListsServer);
  const { microphoneDeviceId, speakerDeviceId, microphoneVolume, speakerVolume } = useSyncExternalStore(
    subscribeSelection,
    readSelection,
    () => SERVER_SELECTION,
  );

  useEffect(() => {
    const refreshOnChange = () => {
      void loadDeviceLists();
    };
    navigator.mediaDevices?.addEventListener?.("devicechange", refreshOnChange);
    return () => {
      navigator.mediaDevices?.removeEventListener?.("devicechange", refreshOnChange);
    };
  }, []);

  const selectMicrophone = useCallback((deviceId: string | undefined) => {
    updateSelection({ microphoneDeviceId: deviceId }, MICROPHONE_STORAGE_KEY, deviceId);
  }, []);

  const selectSpeaker = useCallback((deviceId: string | undefined) => {
    updateSelection({ speakerDeviceId: deviceId }, SPEAKER_STORAGE_KEY, deviceId);
  }, []);

  const setMicrophoneVolume = useCallback((percent: number) => {
    const volume = clampVolume(percent, MAX_MICROPHONE_VOLUME_PERCENT);
    updateSelection({ microphoneVolume: volume }, MICROPHONE_VOLUME_STORAGE_KEY, String(volume));
  }, []);

  const setSpeakerVolume = useCallback((percent: number) => {
    const volume = clampVolume(percent, MAX_SPEAKER_VOLUME_PERCENT);
    updateSelection({ speakerVolume: volume }, SPEAKER_VOLUME_STORAGE_KEY, String(volume));
  }, []);

  return {
    microphones,
    speakers,
    microphoneDeviceId,
    speakerDeviceId,
    microphoneVolume,
    speakerVolume,
    selectMicrophone,
    selectSpeaker,
    setMicrophoneVolume,
    setSpeakerVolume,
    refreshDevices: loadDeviceLists,
  };
}
