import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

import { getSongCaptureStream, startSongCapture, stopSongCapture } from "@/lib/song-capture";
import { recordAudioShareOutcome } from "@/lib/audio-share-outcome";
import { applySongOpusParameters, SONG_BITRATE_BITS_PER_SECOND, useSongMesh, type SongMeshOptions } from "./use-song-mesh";

const SESSION_ID = 77;
const GROUP_ID = 12;
const CALLER_USER_ID = 4;
const OTHER_USER_ID = 9;

const CHROME_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

const OPUS_SDP = [
  "v=0",
  "m=audio 9 UDP/TLS/RTP/SAVPF 111",
  "a=rtpmap:111 opus/48000/2",
  "a=fmtp:111 minptime=10;useinbandfec=1",
  "",
].join("\r\n");

const mocks = vi.hoisted(() => ({
  getDisplayMedia: vi.fn(),
  stompSubscribe: vi.fn(),
  stompPublish: vi.fn(),
  recordAudioShareOutcome: vi.fn(),
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  useGetVoiceTurnCredentials: () => ({ data: undefined }),
}));

vi.mock("@/lib/audio-share-outcome", () => ({
  recordAudioShareOutcome: mocks.recordAudioShareOutcome,
}));

let stompClientOptions: { onConnect?: () => void } | undefined;

vi.mock("@stomp/stompjs", () => ({
  Client: vi.fn(function createClient(options: { onConnect?: () => void }) {
    stompClientOptions = options;
    return {
      connected: true,
      activate: vi.fn(),
      deactivate: vi.fn(),
      subscribe: mocks.stompSubscribe,
      publish: mocks.stompPublish,
    };
  }),
}));

interface FakeTrack {
  kind: string;
  muted: boolean;
  enabled: boolean;
  contentHint: string;
  stop: ReturnType<typeof vi.fn>;
  getSettings: () => MediaTrackSettings;
  addEventListener: (type: string, handler: () => void) => void;
  removeEventListener: (type: string, handler: () => void) => void;
  fire: (type: string) => void;
}

function createFakeTrack(kind: string, settings: MediaTrackSettings = {}): FakeTrack {
  const handlers = new Map<string, Set<() => void>>();
  return {
    kind,
    muted: false,
    enabled: true,
    contentHint: "",
    stop: vi.fn(),
    getSettings: () => settings,
    addEventListener: (type, handler) => {
      const registeredHandlers = handlers.get(type) ?? new Set<() => void>();
      registeredHandlers.add(handler);
      handlers.set(type, registeredHandlers);
    },
    removeEventListener: (type, handler) => {
      handlers.get(type)?.delete(handler);
    },
    fire: (type) => {
      handlers.get(type)?.forEach((handler) => handler());
    },
  };
}

function createDisplayStream(options: { withAudioTrack: boolean }) {
  const videoTrack = createFakeTrack("video", { displaySurface: "monitor" });
  const audioTrack = options.withAudioTrack ? createFakeTrack("audio") : undefined;
  const displayStream = {
    getVideoTracks: () => [videoTrack],
    getAudioTracks: () => (audioTrack ? [audioTrack] : []),
    getTracks: () => (audioTrack ? [videoTrack, audioTrack] : [videoTrack]),
  };
  return { displayStream, videoTrack, audioTrack };
}

class FakeSender {
  replacedTrack: MediaStreamTrack | null = null;
  replaceTrack = vi.fn(async (track: MediaStreamTrack | null) => {
    this.replacedTrack = track;
  });
  getParameters = vi.fn((): RTCRtpSendParameters => ({ encodings: [{}] } as RTCRtpSendParameters));
  setParameters = vi.fn<(...args: [RTCRtpSendParameters]) => Promise<void>>(async () => {});
}

class FakeTransceiver {
  sender = new FakeSender();
  direction = "sendrecv";
}

class FakePeerConnection {
  static instances: FakePeerConnection[] = [];
  targetMemberUserId: number | undefined;
  transceivers: FakeTransceiver[] = [];
  localDescription: RTCSessionDescriptionInit | null = null;
  remoteDescription: RTCSessionDescriptionInit | null = null;
  connectionState = "connected";
  onicecandidate: ((event: { candidate: { toJSON: () => RTCIceCandidateInit } | null }) => void) | null = null;
  ontrack: ((event: RTCTrackEvent) => void) | null = null;
  onconnectionstatechange: (() => void) | null = null;
  close = vi.fn();
  addIceCandidate = vi.fn(async () => {});
  getTransceivers = vi.fn(() => this.transceivers);
  addTransceiver = vi.fn((_kind: string, init: RTCRtpTransceiverInit) => {
    const transceiver = new FakeTransceiver();
    transceiver.direction = init.direction ?? "sendrecv";
    this.transceivers.push(transceiver);
    return transceiver;
  });
  createOffer = vi.fn(async (): Promise<RTCSessionDescriptionInit> => ({ type: "offer", sdp: OPUS_SDP }));
  createAnswer = vi.fn(async (): Promise<RTCSessionDescriptionInit> => ({ type: "answer", sdp: OPUS_SDP }));
  setLocalDescription = vi.fn(async (description: RTCSessionDescriptionInit) => {
    this.localDescription = description;
  });
  setRemoteDescription = vi.fn(async (description: RTCSessionDescriptionInit) => {
    this.remoteDescription = description;
  });

  constructor() {
    FakePeerConnection.instances.push(this);
  }
}

class FakeMediaStream {
  constructor(private readonly tracks: FakeTrack[] = []) {}
  getAudioTracks() {
    return this.tracks.filter((track) => track.kind === "audio");
  }
  getVideoTracks() {
    return this.tracks.filter((track) => track.kind === "video");
  }
  getTracks() {
    return this.tracks;
  }
}

function baseOptions(overrides: Partial<SongMeshOptions> = {}): SongMeshOptions {
  return {
    sessionId: SESSION_ID,
    groupId: GROUP_ID,
    currentUserId: CALLER_USER_ID,
    playerUserIds: [CALLER_USER_ID, OTHER_USER_ID],
    isDjForCurrentRound: false,
    isSongRevealed: false,
    hasSessionEnded: false,
    isPlayerInSession: true,
    hasGroupMembership: true,
    ...overrides,
  };
}

function latestPeer(): FakePeerConnection {
  const peer = FakePeerConnection.instances.at(-1);
  if (!peer) throw new Error("No peer connection was created");
  return peer;
}

function attachedTrack(peer: FakePeerConnection): MediaStreamTrack | null {
  const [transceiver] = peer.transceivers;
  const [track] = transceiver.sender.replaceTrack.mock.lastCall ?? [];
  return track ?? null;
}

function latestSenderEncoding(peer: FakePeerConnection): RTCRtpEncodingParameters {
  const [transceiver] = peer.transceivers;
  const [parameters] = transceiver.sender.setParameters.mock.lastCall ?? [];
  if (!parameters) throw new Error("Sender parameters were never set");
  const [encoding] = parameters.encodings;
  if (!encoding) throw new Error("Sender parameters carried no encoding");
  return encoding;
}

async function connectSignals() {
  await act(async () => {
    stompClientOptions?.onConnect?.();
  });
}

let playImplementation: () => Promise<void> = () => Promise.resolve();

function stubMediaDevices() {
  Object.defineProperty(window.navigator, "mediaDevices", {
    value: { getDisplayMedia: mocks.getDisplayMedia },
    configurable: true,
  });
  vi.stubGlobal("RTCPeerConnection", FakePeerConnection);
  vi.stubGlobal("MediaStream", FakeMediaStream);
}

function resetHookMocks() {
  FakePeerConnection.instances.length = 0;
  stompClientOptions = undefined;
  playImplementation = () => Promise.resolve();
  mocks.getDisplayMedia.mockReset();
  mocks.stompPublish.mockClear();
  mocks.stompSubscribe.mockClear();
  mocks.recordAudioShareOutcome.mockClear();
}

// jsdom's media methods are stubs that log "not implemented"; replaced once for the file
// so a real HTMLMediaElement can back the hook's audio elements.
beforeAll(() => {
  window.HTMLMediaElement.prototype.play = () => playImplementation();
  window.HTMLMediaElement.prototype.pause = () => {};
});

describe("applySongOpusParameters", () => {
  it("writes the stereo music parameters and the named bitrate into the opus fmtp line", () => {
    const munged = applySongOpusParameters(OPUS_SDP, SONG_BITRATE_BITS_PER_SECOND);

    expect(SONG_BITRATE_BITS_PER_SECOND).toBe(128_000);
    expect(munged).toContain("stereo=1");
    expect(munged).toContain("sprop-stereo=1");
    expect(munged).toContain("useinbandfec=1");
    expect(munged).toContain("maxplaybackrate=48000");
    expect(munged).toContain(`maxaveragebitrate=${SONG_BITRATE_BITS_PER_SECOND}`);
  });

  it("leaves an sdp with no opus payload untouched", () => {
    const nonOpus = "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 0\r\na=rtpmap:0 PCMU/8000\r\n";
    expect(applySongOpusParameters(nonOpus, SONG_BITRATE_BITS_PER_SECOND)).toBe(nonOpus);
  });
});

describe("useSongMesh mesh setup", () => {
  beforeEach(() => {
    resetHookMocks();
    stubMediaDevices();
  });

  afterEach(() => {
    stopSongCapture();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("opens a sendrecv audio peer connection to a listener who never joined voice", async () => {
    const { result } = renderHook(() => useSongMesh(baseOptions()));
    await connectSignals();

    const [transceiver] = latestPeer().transceivers;
    expect(transceiver.direction).toBe("sendrecv");
    expect(attachedTrack(latestPeer())).toBeNull();
    expect(result.current.isSignalConnected).toBe(true);
  });

  it("subscribes to the session's song signal queue", async () => {
    renderHook(() => useSongMesh(baseOptions()));
    await connectSignals();

    expect(mocks.stompSubscribe).toHaveBeenCalledWith(`/user/queue/sessions/${SESSION_ID}/song-signal`, expect.any(Function));
  });

  it("sends the munged offer and caps the sender bitrate with the named constant", async () => {
    renderHook(() => useSongMesh(baseOptions()));
    await connectSignals();

    const offerCall = mocks.stompPublish.mock.calls.find(([message]) => JSON.parse(message.body).type === "OFFER");
    expect(offerCall).toBeDefined();
    const { payload } = JSON.parse(offerCall?.[0].body);
    expect(payload).toContain(`maxaveragebitrate=${SONG_BITRATE_BITS_PER_SECOND}`);
    expect(payload).toContain("stereo=1");

    expect(latestSenderEncoding(latestPeer()).maxBitrate).toBe(SONG_BITRATE_BITS_PER_SECOND);
  });});

describe("useSongMesh capture and sender track", () => {
  const callerOptions = baseOptions({ isDjForCurrentRound: true });
  let display: ReturnType<typeof createDisplayStream>;

  beforeEach(() => {
    resetHookMocks();
    display = createDisplayStream({ withAudioTrack: true });
    mocks.getDisplayMedia.mockResolvedValue(display.displayStream);
    stubMediaDevices();
  });

  afterEach(() => {
    stopSongCapture();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("attaches the captured track during the DJ turn and detaches it at the reveal", async () => {
    const { result, rerender } = renderHook((options: SongMeshOptions) => useSongMesh(options), { initialProps: callerOptions });
    await connectSignals();
    await act(async () => {
      await startSongCapture();
    });

    expect(attachedTrack(latestPeer())).toBe(display.audioTrack);
    expect(result.current.visualizerStream).toBe(getSongCaptureStream());

    rerender({ ...callerOptions, isSongRevealed: true });
    expect(attachedTrack(latestPeer())).toBeNull();
  });

  it("detaches the song sender without touching the capture track or any voice element", async () => {
    const voiceAudio = new Audio();
    voiceAudio.volume = 0.7;
    const voicePause = vi.spyOn(voiceAudio, "pause");

    const { rerender } = renderHook((options: SongMeshOptions) => useSongMesh(options), { initialProps: callerOptions });
    await connectSignals();
    await act(async () => {
      await startSongCapture();
    });

    rerender({ ...callerOptions, isSongRevealed: true });

    expect(attachedTrack(latestPeer())).toBeNull();
    expect(display.audioTrack?.stop).not.toHaveBeenCalled();
    expect(display.audioTrack?.enabled).toBe(true);
    expect(voicePause).not.toHaveBeenCalled();
    expect(voiceAudio.volume).toBeCloseTo(0.7);
  });

  it("asks for the share once per match and reuses it for every turn", async () => {
    const { result } = renderHook(() => useSongMesh(callerOptions));

    await act(async () => {
      await startSongCapture();
    });
    await act(async () => {
      await startSongCapture();
    });

    expect(mocks.getDisplayMedia).toHaveBeenCalledTimes(1);
    expect(result.current.isCapturing).toBe(true);
  });

  it("records the share outcome and names the browser when no audio comes back", async () => {
    Object.defineProperty(window.navigator, "userAgent", { value: CHROME_USER_AGENT, configurable: true });
    const silentDisplay = createDisplayStream({ withAudioTrack: false });
    mocks.getDisplayMedia.mockReset();
    mocks.getDisplayMedia.mockResolvedValue(silentDisplay.displayStream);

    const { result } = renderHook(() => useSongMesh(callerOptions));
    let shareResult: Awaited<ReturnType<typeof startSongCapture>> | undefined;
    await act(async () => {
      shareResult = await startSongCapture();
    });

    expect(shareResult?.errorKind).toBe("no-audio-track");
    expect(result.current.isCapturing).toBe(false);
    expect(shareResult?.errorMessage).toContain("Chrome");
    expect(shareResult?.errorMessage).toContain("try again");
    expect(recordAudioShareOutcome).toHaveBeenCalledWith(expect.objectContaining({ browser: "Chrome", displaySurface: "monitor", audioTrackCount: 0 }));
  });
});

describe("useSongMesh remote playback", () => {
  beforeEach(() => {
    resetHookMocks();
    stubMediaDevices();
  });

  afterEach(() => {
    stopSongCapture();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("flags blocked autoplay and clears the flag when playback is retried", async () => {
    playImplementation = () => Promise.reject(new Error("blocked"));

    const { result } = renderHook(() => useSongMesh(baseOptions()));
    await connectSignals();

    const remoteTrack = createFakeTrack("audio");
    const remoteStream = new FakeMediaStream([remoteTrack]);
    await act(async () => {
      latestPeer().ontrack?.({ streams: [remoteStream], track: remoteTrack } as unknown as RTCTrackEvent);
    });

    expect(result.current.isPlaybackBlocked).toBe(true);

    playImplementation = () => Promise.resolve();
    await act(async () => {
      result.current.resumePlayback();
    });

    expect(result.current.isPlaybackBlocked).toBe(false);
  });

  it("follows the incoming unmuted track with the visualizer and the song volume", async () => {
    const volumeSpy = vi.spyOn(window.HTMLMediaElement.prototype, "volume", "set");

    const { result, rerender } = renderHook((options: SongMeshOptions) => useSongMesh(options), { initialProps: baseOptions({ songVolume: 40 }) });
    await connectSignals();

    const remoteTrack = createFakeTrack("audio");
    const remoteStream = new FakeMediaStream([remoteTrack]);
    act(() => {
      latestPeer().ontrack?.({ streams: [remoteStream], track: remoteTrack } as unknown as RTCTrackEvent);
    });

    expect(result.current.visualizerStream).toBe(remoteStream);
    const [initialVolume] = volumeSpy.mock.lastCall ?? [];
    expect(initialVolume).toBeCloseTo(0.4);

    rerender(baseOptions({ songVolume: 80 }));
    const [updatedVolume] = volumeSpy.mock.lastCall ?? [];
    expect(updatedVolume).toBeCloseTo(0.8);
  });

  it("mutes only the incoming song audio while the active player is silenced", async () => {
    const mutedSpy = vi.spyOn(window.HTMLMediaElement.prototype, "muted", "set");

    const { result, rerender } = renderHook((options: SongMeshOptions) => useSongMesh(options), { initialProps: baseOptions() });
    await connectSignals();

    const remoteTrack = createFakeTrack("audio");
    const remoteStream = new FakeMediaStream([remoteTrack]);
    act(() => {
      latestPeer().ontrack?.({ streams: [remoteStream], track: remoteTrack } as unknown as RTCTrackEvent);
    });

    const [initialMuted] = mutedSpy.mock.lastCall ?? [];
    expect(initialMuted).toBe(false);
    expect(result.current.visualizerStream).toBe(remoteStream);

    rerender(baseOptions({ isSongSilenced: true }));

    const [silencedMuted] = mutedSpy.mock.lastCall ?? [];
    expect(silencedMuted).toBe(true);
    expect(remoteTrack.stop).not.toHaveBeenCalled();
  });

  it("mutes incoming song audio while the game is paused", async () => {
    const mutedSpy = vi.spyOn(window.HTMLMediaElement.prototype, "muted", "set");

    const { rerender } = renderHook((options: SongMeshOptions) => useSongMesh(options), { initialProps: baseOptions() });
    await connectSignals();

    const remoteTrack = createFakeTrack("audio");
    const remoteStream = new FakeMediaStream([remoteTrack]);
    act(() => {
      latestPeer().ontrack?.({ streams: [remoteStream], track: remoteTrack } as unknown as RTCTrackEvent);
    });

    rerender(baseOptions({ isSongPaused: true }));

    const [pausedMuted] = mutedSpy.mock.lastCall ?? [];
    expect(pausedMuted).toBe(true);
  });
});

describe("useSongMesh stop conditions", () => {
  const callerOptions = baseOptions({ isDjForCurrentRound: true });
  let display: ReturnType<typeof createDisplayStream>;

  beforeEach(() => {
    resetHookMocks();
    display = createDisplayStream({ withAudioTrack: true });
    mocks.getDisplayMedia.mockResolvedValue(display.displayStream);
    stubMediaDevices();
  });

  afterEach(() => {
    stopSongCapture();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  async function renderWithCapture() {
    const rendered = renderHook((options: SongMeshOptions) => useSongMesh(options), { initialProps: callerOptions });
    await connectSignals();
    await act(async () => {
      await startSongCapture();
    });
    return { ...rendered, peer: latestPeer() };
  }

  it("detaches the DJ track while paused and reattaches it when resumed", async () => {
    const { peer, rerender } = await renderWithCapture();
    expect(attachedTrack(peer)).toBe(display.audioTrack);

    rerender({ ...callerOptions, isSongPaused: true });
    expect(attachedTrack(peer)).toBeNull();

    rerender({ ...callerOptions, isSongPaused: false });
    expect(attachedTrack(peer)).toBe(display.audioTrack);
  });

  it("stops the capture and detaches the sender when the session ends", async () => {
    const { peer, rerender } = await renderWithCapture();
    rerender({ ...callerOptions, hasSessionEnded: true });

    expect(getSongCaptureStream()).toBeNull();
    expect(display.audioTrack?.stop).toHaveBeenCalled();
    expect(attachedTrack(peer)).toBeNull();
  });

  it("stops the capture when this user is no longer a player", async () => {
    const { peer, rerender } = await renderWithCapture();
    rerender({ ...callerOptions, isPlayerInSession: false });

    expect(getSongCaptureStream()).toBeNull();
    expect(display.audioTrack?.stop).toHaveBeenCalled();
    expect(attachedTrack(peer)).toBeNull();
  });

  it("stops the capture when the group membership ends", async () => {
    const { rerender } = await renderWithCapture();
    rerender({ ...callerOptions, hasGroupMembership: false });

    expect(getSongCaptureStream()).toBeNull();
    expect(display.audioTrack?.stop).toHaveBeenCalled();
  });

  it("stops the capture when the page is hidden", async () => {
    await renderWithCapture();
    act(() => {
      window.dispatchEvent(new Event("pagehide"));
    });

    expect(getSongCaptureStream()).toBeNull();
    expect(display.audioTrack?.stop).toHaveBeenCalled();
  });

  it("stops the capture when the share is stopped in the browser bar", async () => {
    await renderWithCapture();
    act(() => {
      display.audioTrack?.fire("ended");
    });

    expect(getSongCaptureStream()).toBeNull();
    expect(display.audioTrack?.stop).toHaveBeenCalled();
  });
});
