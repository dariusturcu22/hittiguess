import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { noiseGateOpenThresholdDb, shouldSilenceDjForActivePlayer, useVoiceMesh } from "./use-voice-mesh";
import { MAX_NOISE_GATE_SENSITIVITY, resetAudioSelection } from "@/hooks/use-audio-devices";

const suppressor = vi.hoisted(() => ({
  gateOptions: [] as Array<Record<string, number>>,
  rnnoiseNodeCount: 0,
  loadRnnoise: vi.fn(async () => new ArrayBuffer(8)),
}));

vi.mock("@sapphi-red/web-noise-suppressor", () => ({
  NoiseGateWorkletNode: class {
    constructor(_context: unknown, options: Record<string, number>) {
      suppressor.gateOptions.push(options);
    }
    connect() {}
    disconnect() {}
  },
  RnnoiseWorkletNode: class {
    constructor() {
      suppressor.rnnoiseNodeCount += 1;
    }
    connect() {}
    disconnect() {}
    destroy() {}
  },
  loadRnnoise: suppressor.loadRnnoise,
}));

function createMicrophoneTrack() {
  return { enabled: true, contentHint: "", stop: vi.fn() };
}

const getUserMedia = vi.fn(async () => {
  const track = createMicrophoneTrack();
  return { getAudioTracks: () => [track], getTracks: () => [track] };
});

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  useGetVoiceTurnCredentials: () => ({ data: undefined }),
}));

const stompSubscribe = vi.fn();
let stompClientOptions: { onConnect?: () => void } | undefined;

vi.mock("@stomp/stompjs", () => ({
  Client: vi.fn(function createClient(options: { onConnect?: () => void }) {
    stompClientOptions = options;
    return { activate: vi.fn(), deactivate: vi.fn(), subscribe: stompSubscribe };
  }),
}));

let workletAddModuleImplementation: (url: string) => Promise<void> = async () => {};

class FakeAudioContext {
  currentTime = 0;
  audioWorklet = { addModule: (url: string) => workletAddModuleImplementation(url) };
  resume() {
    return Promise.resolve();
  }
  close() {
    return Promise.resolve();
  }
  createMediaStreamDestination() {
    return { stream: { getAudioTracks: () => [{ enabled: true, stop: vi.fn() }] } };
  }
  createMediaStreamSource() {
    return { connect: vi.fn(), disconnect: vi.fn() };
  }
  createGain() {
    return { gain: { setValueAtTime: vi.fn() }, connect: vi.fn(), disconnect: vi.fn() };
  }
}

function defineMediaDevices(mediaDevices: unknown) {
  Object.defineProperty(window.navigator, "mediaDevices", { value: mediaDevices, configurable: true });
}

beforeEach(() => {
  window.localStorage.clear();
  resetAudioSelection();
  suppressor.gateOptions.length = 0;
  suppressor.rnnoiseNodeCount = 0;
  suppressor.loadRnnoise.mockClear();
  workletAddModuleImplementation = async () => {};
  Object.defineProperty(window, "AudioContext", { value: FakeAudioContext, configurable: true });
});

describe("useVoiceMesh device selection", () => {
  it("opens the selected microphone with explicit processing and an exact device constraint", async () => {
    defineMediaDevices({ getUserMedia });
    getUserMedia.mockClear();

    const { result } = renderHook(() =>
      useVoiceMesh(4, 11, [], false, { microphoneDeviceId: "mic-9" }),
    );

    let opened = false;
    await act(async () => {
      opened = await result.current.startMicrophone();
    });

    expect(opened).toBe(true);
    expect(getUserMedia).toHaveBeenCalledWith({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
        channelCount: 1,
        deviceId: { exact: "mic-9" },
      },
    });
  });

  it("opens the default microphone with the same explicit processing", async () => {
    defineMediaDevices({ getUserMedia });
    getUserMedia.mockClear();

    const { result } = renderHook(() => useVoiceMesh(4, 11, [], false, {}));

    await act(async () => {
      await result.current.startMicrophone();
    });

    expect(getUserMedia).toHaveBeenCalledWith({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
        channelCount: 1,
      },
    });
  });

  it("reports an insecure context instead of a permission denial", async () => {
    defineMediaDevices(undefined);

    const { result } = renderHook(() => useVoiceMesh(4, 11, [], false, {}));

    let opened = true;
    await act(async () => {
      opened = await result.current.startMicrophone();
    });

    expect(opened).toBe(false);
    expect(result.current.microphoneError).toBe(true);
    expect(result.current.microphoneErrorMessage).toBe("Talking needs the game open over HTTPS. You can still listen.");
  });

  it("names permission denial and missing devices distinctly", async () => {
    const failingMediaDevices = { getUserMedia: vi.fn() };
    defineMediaDevices(failingMediaDevices);

    const { result } = renderHook(() => useVoiceMesh(4, 11, [], false, {}));

    failingMediaDevices.getUserMedia.mockRejectedValueOnce(new DOMException("denied", "NotAllowedError"));
    await act(async () => {
      await result.current.startMicrophone();
    });
    expect(result.current.microphoneErrorMessage).toBe("Microphone permission is needed to talk. You can still listen.");

    failingMediaDevices.getUserMedia.mockRejectedValueOnce(new DOMException("none", "NotFoundError"));
    await act(async () => {
      await result.current.startMicrophone();
    });
    expect(result.current.microphoneErrorMessage).toBe("No microphone was found. You can still listen.");
  });
});

describe("shouldSilenceDjForActivePlayer", () => {
  const ACTIVE_PLAYER_ID = 3;

  it("silences the DJ for the active player from lock-in until the next round", () => {
    for (const status of ["COUNTDOWN", "BETTING", "REVEALED", "SCORED"]) {
      expect(shouldSilenceDjForActivePlayer({ activePlayerId: ACTIVE_PLAYER_ID, status }, ACTIVE_PLAYER_ID)).toBe(true);
    }
  });

  it("keeps the song audible while the active player is still placing", () => {
    expect(shouldSilenceDjForActivePlayer({ activePlayerId: ACTIVE_PLAYER_ID, status: "AWAITING_PLACEMENT" }, ACTIVE_PLAYER_ID)).toBe(false);
  });

  it("never silences the DJ for anyone other than the active player", () => {
    expect(shouldSilenceDjForActivePlayer({ activePlayerId: ACTIVE_PLAYER_ID, status: "BETTING" }, ACTIVE_PLAYER_ID + 1)).toBe(false);
  });
});

describe("noiseGateOpenThresholdDb", () => {
  it("raises the open threshold with sensitivity and clamps out-of-range input", () => {
    const quietestThreshold = noiseGateOpenThresholdDb(0);
    const loudestThreshold = noiseGateOpenThresholdDb(MAX_NOISE_GATE_SENSITIVITY);

    expect(quietestThreshold).toBeLessThan(loudestThreshold);
    expect(noiseGateOpenThresholdDb(MAX_NOISE_GATE_SENSITIVITY / 2)).toBe((quietestThreshold + loudestThreshold) / 2);
    expect(noiseGateOpenThresholdDb(-20)).toBe(quietestThreshold);
    expect(noiseGateOpenThresholdDb(MAX_NOISE_GATE_SENSITIVITY * 2)).toBe(loudestThreshold);
  });
});

describe("useVoiceMesh noise suppression", () => {
  it("configures the noise gate from the stored sensitivity as a single channel", async () => {
    const storedSensitivity = 80;
    window.localStorage.setItem("hittiguess-noise-gate-sensitivity", String(storedSensitivity));
    resetAudioSelection();
    defineMediaDevices({ getUserMedia });

    const { result } = renderHook(() => useVoiceMesh(4, 11, [], false, {}));
    await act(async () => {
      await result.current.startMicrophone();
    });

    expect(suppressor.gateOptions).toHaveLength(1);
    const gateOptions = suppressor.gateOptions.at(0);
    expect(gateOptions).toMatchObject({
      openThreshold: noiseGateOpenThresholdDb(storedSensitivity),
      maxChannels: 1,
    });
    expect(gateOptions?.closeThreshold).toBeLessThan(gateOptions?.openThreshold ?? 0);
  });

  it("keeps the call working when the worklet modules fail to load", async () => {
    defineMediaDevices({ getUserMedia });
    workletAddModuleImplementation = async () => {
      throw new Error("worklet unavailable");
    };

    const { result } = renderHook(() => useVoiceMesh(4, 11, [], false, {}));
    let opened = false;
    await act(async () => {
      opened = await result.current.startMicrophone();
    });

    expect(opened).toBe(true);
    expect(suppressor.gateOptions).toHaveLength(0);
    expect(suppressor.rnnoiseNodeCount).toBe(0);
  });

  it("skips RNNoise but keeps the call working when the wasm fails to load", async () => {
    defineMediaDevices({ getUserMedia });
    suppressor.loadRnnoise.mockRejectedValueOnce(new Error("wasm unavailable"));

    const { result } = renderHook(() => useVoiceMesh(4, 11, [], false, {}));
    let opened = false;
    await act(async () => {
      opened = await result.current.startMicrophone();
    });

    expect(opened).toBe(true);
    expect(suppressor.rnnoiseNodeCount).toBe(0);
  });
});

describe("useVoiceMesh signaling", () => {
  const GROUP_ID = 4;
  const CURRENT_USER_ID = 11;

  it("receives signals on its own user queue and presence on the group voice topic", () => {
    stompSubscribe.mockClear();
    stompClientOptions = undefined;
    renderHook(() => useVoiceMesh(GROUP_ID, CURRENT_USER_ID, [], true, {}));

    act(() => {
      stompClientOptions?.onConnect?.();
    });

    const subscribedDestinations = stompSubscribe.mock.calls.map(([destination]) => destination);
    expect(subscribedDestinations).toEqual([
      `/topic/groups/${GROUP_ID}/voice`,
      `/user/queue/groups/${GROUP_ID}/voice-signal`,
    ]);
  });
});
