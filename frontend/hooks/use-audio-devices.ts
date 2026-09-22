"use client";

import { useCallback, useEffect, useState, useSyncExternalStore } from "react";

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
const EMPTY_LISTS: DeviceLists = { microphones: [], speakers: [] };

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

function persistDeviceId(key: string, deviceId: string | undefined) {
  if (typeof window === "undefined") {
    return;
  }
  if (deviceId === undefined) {
    window.localStorage.removeItem(key);
  } else {
    window.localStorage.setItem(key, deviceId);
  }
}

// Lists the browser's audio inputs and outputs for the voice settings popup.
// Device labels stay empty until microphone permission is granted; selection
// persists in local storage so it survives reloads.
export function useAudioDevices() {
  const { microphones, speakers } = useSyncExternalStore(subscribeLists, snapshotLists, snapshotListsServer);
  const [microphoneDeviceId, setMicrophoneDeviceId] = useState<string | undefined>(() =>
    storedDeviceId(MICROPHONE_STORAGE_KEY),
  );
  const [speakerDeviceId, setSpeakerDeviceId] = useState<string | undefined>(() =>
    storedDeviceId(SPEAKER_STORAGE_KEY),
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
    setMicrophoneDeviceId(deviceId);
    persistDeviceId(MICROPHONE_STORAGE_KEY, deviceId);
  }, []);

  const selectSpeaker = useCallback((deviceId: string | undefined) => {
    setSpeakerDeviceId(deviceId);
    persistDeviceId(SPEAKER_STORAGE_KEY, deviceId);
  }, []);

  return {
    microphones,
    speakers,
    microphoneDeviceId,
    speakerDeviceId,
    selectMicrophone,
    selectSpeaker,
    refreshDevices: loadDeviceLists,
  };
}
