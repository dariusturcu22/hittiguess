"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";

import type { NoiseGateWorkletNode, RnnoiseWorkletNode } from "@sapphi-red/web-noise-suppressor";
import { useGetVoiceTurnCredentials } from "@/hooks/generated/group-management/group-management";
import type { MemberDTO } from "@/hooks/models/memberDTO";
import { MAX_NOISE_GATE_SENSITIVITY, useAudioDevices } from "@/hooks/use-audio-devices";
import { observeMicrophoneSpeaking, parseVoiceStatus, sendVoiceStatus, VOICE_STATUS_CHANNEL, type VoiceStatus } from "@/lib/voice-status";

const WEBSOCKET_PATH = "/ws";
const VOICE_TOPIC_PREFIX = "/topic/groups";
const VOICE_SIGNAL_PREFIX = "/app/groups";
const VOICE_SIGNAL_QUEUE_PREFIX = "/user/queue/groups";
const RECONNECT_DELAY_MILLISECONDS = 3_000;
const PEER_CONNECT_TIMEOUT_MILLISECONDS = 8_000;
const OFFER_SIGNAL = "OFFER";
const ANSWER_SIGNAL = "ANSWER";
const CANDIDATE_SIGNAL = "CANDIDATE";
const VOICE_PRESENCE_CHANGED_EVENT = "VOICE_PRESENCE_CHANGED";
const AUDIO_KIND = "audio";
const PERCENT = 100;
const SEND_AND_RECEIVE = "sendrecv";
const NOT_ALLOWED_ERROR_NAME = "NotAllowedError";
const NOT_FOUND_ERROR_NAME = "NotFoundError";
const OVERCONSTRAINED_ERROR_NAME = "OverconstrainedError";
const INSECURE_CONTEXT_MESSAGE = "Talking needs the game open over HTTPS. You can still listen.";
const MICROPHONE_PERMISSION_MESSAGE = "Microphone permission is needed to talk. You can still listen.";
const MICROPHONE_NOT_FOUND_MESSAGE = "No microphone was found. You can still listen.";
const MICROPHONE_START_FAILED_MESSAGE = "Could not start the microphone. You can still listen.";

const MAX_VOICE_CHANNELS = 1;
const MICROPHONE_SPEECH_CONTENT_HINT = "speech";
const VOICE_MAX_BITRATE_BITS_PER_SECOND = 40_000;

// The hold is the tail the gate keeps open after the level drops, long enough that a pause
// between words does not chop the next one. The close threshold sits under the open one so
// a level hovering at the edge does not chatter.
const NOISE_GATE_HOLD_MILLISECONDS = 200;
const NOISE_GATE_CLOSE_THRESHOLD_MARGIN_DB = 8;
const NOISE_GATE_MINIMUM_OPEN_THRESHOLD_DB = -70;
const NOISE_GATE_MAXIMUM_OPEN_THRESHOLD_DB = -20;

const NOISE_GATE_WORKLET_URL = "/rnnoise/noiseGateWorklet.js";
const RNNOISE_WORKLET_URL = "/rnnoise/rnnoiseWorklet.js";
const RNNOISE_WASM_URL = "/rnnoise/rnnoise.wasm";
const RNNOISE_SIMD_WASM_URL = "/rnnoise/rnnoise_simd.wasm";

interface VoiceSignal { type: string; senderUserId: number; targetMemberUserId: number; payload: string; }

interface AudioMixer {
  context: AudioContext;
  destination: MediaStreamAudioDestinationNode;
  microphoneSource: MediaStreamAudioSourceNode | null;
  microphoneGain: GainNode | null;
  noiseGate: NoiseGateWorkletNode | null;
  rnnoise: RnnoiseWorkletNode | null;
}

interface AudioContextWindow extends Window {
  webkitAudioContext?: typeof AudioContext;
}

export const PLACEMENT_LOCKED_STATUSES = new Set(["COUNTDOWN", "BETTING", "REVEALED", "SCORED"]);

// The active player's song audio cuts off from lock-in until the next round starts;
// only the DJ's incoming audio is silenced, so voice chat with everyone else goes on.
export function shouldSilenceDjForActivePlayer(
  round: { activePlayerId?: number; status?: string } | undefined,
  currentPlayerId: number | undefined,
): boolean {
  if (!round || currentPlayerId === undefined || round.activePlayerId !== currentPlayerId) return false;
  return PLACEMENT_LOCKED_STATUSES.has(round.status ?? "");
}

// Higher sensitivity widens the gate: the open threshold rises, so steady background noise
// has to be louder to hold it open. The value runs from effectively always open to speech-only.
export function noiseGateOpenThresholdDb(sensitivityPercent: number): number {
  const clampedSensitivity = Math.min(MAX_NOISE_GATE_SENSITIVITY, Math.max(0, sensitivityPercent));
  const position = clampedSensitivity / MAX_NOISE_GATE_SENSITIVITY;
  return NOISE_GATE_MINIMUM_OPEN_THRESHOLD_DB + position * (NOISE_GATE_MAXIMUM_OPEN_THRESHOLD_DB - NOISE_GATE_MINIMUM_OPEN_THRESHOLD_DB);
}

function describeMicrophoneError(error: unknown): string {
  if (error instanceof DOMException) {
    if (error.name === NOT_ALLOWED_ERROR_NAME) {
      return MICROPHONE_PERMISSION_MESSAGE;
    }
    if (error.name === NOT_FOUND_ERROR_NAME || error.name === OVERCONSTRAINED_ERROR_NAME) {
      return MICROPHONE_NOT_FOUND_MESSAGE;
    }
  }
  return MICROPHONE_START_FAILED_MESSAGE;
}

function websocketUrl(): string {
  const apiUrl = new URL(process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080");
  apiUrl.protocol = apiUrl.protocol === "https:" ? "wss:" : "ws:";
  apiUrl.pathname = WEBSOCKET_PATH;
  apiUrl.search = "";
  return apiUrl.toString();
}

function createAudioMixer(): AudioMixer | null {
  if (typeof window === "undefined") return null;
  const Context = window.AudioContext ?? (window as AudioContextWindow).webkitAudioContext;
  if (!Context) return null;
  const context = new Context();
  void context.resume().catch(() => {});
  return { context, destination: context.createMediaStreamDestination(), microphoneSource: null, microphoneGain: null, noiseGate: null, rnnoise: null };
}

async function loadWorkletModule(context: AudioContext, url: string): Promise<boolean> {
  try {
    await context.audioWorklet.addModule(url);
    return true;
  } catch {
    return false;
  }
}

async function createNoiseGateNode(context: AudioContext, sensitivityPercent: number): Promise<NoiseGateWorkletNode | null> {
  if (!(await loadWorkletModule(context, NOISE_GATE_WORKLET_URL))) return null;
  const openThreshold = noiseGateOpenThresholdDb(sensitivityPercent);
  try {
    const { NoiseGateWorkletNode: WorkletNode } = await import("@sapphi-red/web-noise-suppressor");
    return new WorkletNode(context, {
      openThreshold,
      closeThreshold: openThreshold - NOISE_GATE_CLOSE_THRESHOLD_MARGIN_DB,
      holdMs: NOISE_GATE_HOLD_MILLISECONDS,
      maxChannels: MAX_VOICE_CHANNELS,
    });
  } catch {
    return null;
  }
}

// RNNoise is optional: a failed worklet or wasm fetch leaves the plain microphone path intact.
async function createRnnoiseNode(context: AudioContext): Promise<RnnoiseWorkletNode | null> {
  if (!(await loadWorkletModule(context, RNNOISE_WORKLET_URL))) return null;
  try {
    const { RnnoiseWorkletNode: WorkletNode, loadRnnoise } = await import("@sapphi-red/web-noise-suppressor");
    const wasmBinary = await loadRnnoise({ url: RNNOISE_WASM_URL, simdUrl: RNNOISE_SIMD_WASM_URL });
    return new WorkletNode(context, { maxChannels: MAX_VOICE_CHANNELS, wasmBinary });
  } catch {
    return null;
  }
}

function disconnectMicrophoneGraph(mixer: AudioMixer) {
  mixer.noiseGate?.disconnect();
  mixer.rnnoise?.disconnect();
  mixer.rnnoise?.destroy();
  mixer.microphoneSource?.disconnect();
  mixer.microphoneGain?.disconnect();
  mixer.microphoneSource = null;
  mixer.noiseGate = null;
  mixer.rnnoise = null;
  mixer.microphoneGain = null;
}

// The outgoing chain is microphone, optional gate, optional RNNoise, then the volume gain.
// Each filter is skipped when its worklet or wasm does not load, so the chain always ends
// at the destination and the call keeps working.
async function connectMicrophoneGraph(
  mixer: AudioMixer,
  stream: MediaStream,
  microphoneVolumePercent: number,
  noiseGateSensitivity: number,
  isRnnoiseEnabled: boolean,
) {
  disconnectMicrophoneGraph(mixer);
  const source = mixer.context.createMediaStreamSource(stream);
  const gain = mixer.context.createGain();
  gain.gain.setValueAtTime(microphoneVolumePercent / PERCENT, mixer.context.currentTime);
  let chainTail: AudioNode = source;
  if (noiseGateSensitivity > 0) {
    const noiseGate = await createNoiseGateNode(mixer.context, noiseGateSensitivity);
    if (noiseGate) {
      chainTail.connect(noiseGate);
      chainTail = noiseGate;
      mixer.noiseGate = noiseGate;
    }
  }
  if (isRnnoiseEnabled) {
    const rnnoise = await createRnnoiseNode(mixer.context);
    if (rnnoise) {
      chainTail.connect(rnnoise);
      chainTail = rnnoise;
      mixer.rnnoise = rnnoise;
    }
  }
  chainTail.connect(gain);
  gain.connect(mixer.destination);
  mixer.microphoneSource = source;
  mixer.microphoneGain = gain;
}

function microphoneConstraints(deviceId: string | undefined): MediaStreamConstraints {
  const audio: MediaTrackConstraints = {
    echoCancellation: true,
    noiseSuppression: true,
    autoGainControl: true,
    channelCount: MAX_VOICE_CHANNELS,
  };
  if (deviceId) audio.deviceId = { exact: deviceId };
  return { audio };
}

async function capSenderBitrate(sender: RTCRtpSender): Promise<void> {
  try {
    const parameters = sender.getParameters();
    const encodings = parameters.encodings?.length ? parameters.encodings : [{}];
    encodings.forEach((encoding) => { encoding.maxBitrate = VOICE_MAX_BITRATE_BITS_PER_SECOND; });
    parameters.encodings = encodings;
    await sender.setParameters(parameters);
  } catch {
    // A sender can reject parameters until its negotiation settles; the cap is best effort.
  }
}

interface VoiceMeshOptions {
  microphoneDeviceId?: string;
  speakerDeviceId?: string;
  // Percentages from the voice settings: the microphone's gain on the outgoing mix and
  // the volume of everyone else's incoming audio.
  microphoneVolume?: number;
  speakerVolume?: number;
  onPresenceChange?: () => void;
}

// A full mesh of peer connections, one per other voice member. Every peer carries a single
// outgoing audio track: this player's microphone, passed through an optional noise gate and
// RNNoise filter so the track stays clean. The track itself never changes, so a player with
// no microphone still joins as a listener.
export function useVoiceMesh(groupId: number, currentUserId: number | undefined, voiceMembers: MemberDTO[], isInVoice: boolean, options: VoiceMeshOptions = {}) {
  const { microphoneDeviceId, speakerDeviceId, microphoneVolume = 100, speakerVolume = 100, onPresenceChange } = options;
  const { noiseGateSensitivity, isRnnoiseEnabled } = useAudioDevices();
  const microphoneVolumeReference = useRef(microphoneVolume);
  const speakerVolumeReference = useRef(speakerVolume);
  const noiseGateSensitivityReference = useRef(noiseGateSensitivity);
  const isRnnoiseEnabledReference = useRef(isRnnoiseEnabled);
  const onPresenceChangeReference = useRef(onPresenceChange);
  useEffect(() => {
    onPresenceChangeReference.current = onPresenceChange;
  });
  const turnCredentialsQuery = useGetVoiceTurnCredentials(groupId, { query: { enabled: isInVoice, retry: false } });
  const clientReference = useRef<Client | null>(null);
  const mixerReference = useRef<AudioMixer | null>(null);
  const microphoneStreamReference = useRef<MediaStream | null>(null);
  const peersReference = useRef(new Map<number, RTCPeerConnection>());
  const pendingCandidatesReference = useRef(new Map<number, RTCIceCandidateInit[]>());
  const remoteAudioReference = useRef(new Map<number, HTMLAudioElement>());
  const statusChannelsReference = useRef(new Map<number, RTCDataChannel>());
  const localStatusReference = useRef<VoiceStatus>({ isMuted: false, isDeafened: false, isSpeaking: false });
  const [memberStatuses, setMemberStatuses] = useState<Record<number, VoiceStatus>>({});
  const [microphoneStream, setMicrophoneStream] = useState<MediaStream | null>(null);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const isDeafenedReference = useRef(false);
  const [isMuted, setIsMuted] = useState(false);
  const [isDeafened, setIsDeafened] = useState(false);
  const [microphoneError, setMicrophoneError] = useState(false);
  const [microphoneErrorMessage, setMicrophoneErrorMessage] = useState<string | null>(null);
  const [isSignalConnected, setIsSignalConnected] = useState(false);
  const [hasOutgoingAudio, setHasOutgoingAudio] = useState(false);
  const [peerResetCount, setPeerResetCount] = useState(0);

  useEffect(() => {
    const status = { isMuted: isMuted || microphoneError, isDeafened, isSpeaking: isSpeaking && !isMuted && !microphoneError };
    localStatusReference.current = status;
    statusChannelsReference.current.forEach((channel) => sendVoiceStatus(channel, status));
  }, [isDeafened, isMuted, isSpeaking, microphoneError]);

  useEffect(() => {
    const context = mixerReference.current?.context;
    if (!isInVoice || !microphoneStream || !context || isMuted) return;
    return observeMicrophoneSpeaking(context, microphoneStream, setIsSpeaking);
  }, [isInVoice, isMuted, microphoneStream]);

  const bindStatusChannel = useCallback((targetMemberUserId: number, channel: RTCDataChannel) => {
    if (channel.label !== VOICE_STATUS_CHANNEL) { channel.close(); return; }
    statusChannelsReference.current.set(targetMemberUserId, channel);
    channel.onopen = () => sendVoiceStatus(channel, localStatusReference.current);
    channel.onmessage = (event) => {
      if (statusChannelsReference.current.get(targetMemberUserId) !== channel) return;
      const status = parseVoiceStatus(event.data);
      if (status) setMemberStatuses((current) => ({ ...current, [targetMemberUserId]: status }));
    };
    channel.onclose = () => {
      if (statusChannelsReference.current.get(targetMemberUserId) !== channel) return;
      statusChannelsReference.current.delete(targetMemberUserId);
      setMemberStatuses((current) => {
        const remaining = { ...current };
        delete remaining[targetMemberUserId];
        return remaining;
      });
    };
  }, []);

  const outgoingTrack = useCallback((): MediaStreamTrack | null => {
    const mixedTrack = mixerReference.current?.destination.stream.getAudioTracks().at(0);
    return mixedTrack ?? microphoneStreamReference.current?.getAudioTracks().at(0) ?? null;
  }, []);

  const ensureMixer = useCallback((): AudioMixer | null => {
    if (!mixerReference.current) {
      mixerReference.current = createAudioMixer();
    } else {
      void mixerReference.current.context.resume().catch(() => {});
    }
    return mixerReference.current;
  }, []);

  const applyVoiceBitrateCap = useCallback((peer: RTCPeerConnection) => {
    peer.getTransceivers().forEach((transceiver) => { void capSenderBitrate(transceiver.sender); });
  }, []);

  const refreshOutgoingTrack = useCallback(() => {
    const track = outgoingTrack();
    setHasOutgoingAudio(true);
    peersReference.current.forEach((peer) => {
      peer.getTransceivers().forEach((transceiver) => { void transceiver.sender.replaceTrack(track); });
      applyVoiceBitrateCap(peer);
    });
  }, [applyVoiceBitrateCap, outgoingTrack]);

  useEffect(() => {
    speakerVolumeReference.current = speakerVolume;
    remoteAudioReference.current.forEach((audio) => { audio.volume = speakerVolume / PERCENT; });
  }, [speakerVolume]);

  useEffect(() => {
    microphoneVolumeReference.current = microphoneVolume;
    const mixer = mixerReference.current;
    mixer?.microphoneGain?.gain.setValueAtTime(microphoneVolume / PERCENT, mixer.context.currentTime);
  }, [microphoneVolume]);

  // A gate or RNNoise change rebuilds the outgoing chain for a live microphone; the tracked
  // destination track is unchanged, so no peer needs to renegotiate.
  useEffect(() => {
    noiseGateSensitivityReference.current = noiseGateSensitivity;
    isRnnoiseEnabledReference.current = isRnnoiseEnabled;
    const mixer = mixerReference.current;
    const stream = microphoneStreamReference.current;
    if (!mixer || !stream) return;
    void connectMicrophoneGraph(mixer, stream, microphoneVolumeReference.current, noiseGateSensitivity, isRnnoiseEnabled).catch(() => {});
  }, [isRnnoiseEnabled, noiseGateSensitivity]);

  const applyRemoteAudioMuting = useCallback(() => {
    remoteAudioReference.current.forEach((audio) => {
      audio.muted = isDeafenedReference.current;
    });
  }, []);

  const sendSignal = useCallback((type: string, targetMemberUserId: number, payload: unknown) => {
    if (!clientReference.current?.connected) return;
    clientReference.current.publish({ destination: `${VOICE_SIGNAL_PREFIX}/${groupId}/voice/signal`, body: JSON.stringify({ type, targetMemberUserId, payload: JSON.stringify(payload) }) });
  }, [groupId]);

  const closePeer = useCallback((targetMemberUserId: number) => {
    statusChannelsReference.current.get(targetMemberUserId)?.close();
    statusChannelsReference.current.delete(targetMemberUserId);
    setMemberStatuses((current) => {
      const remaining = { ...current };
      delete remaining[targetMemberUserId];
      return remaining;
    });
    peersReference.current.get(targetMemberUserId)?.close();
    peersReference.current.delete(targetMemberUserId);
    pendingCandidatesReference.current.delete(targetMemberUserId);
    remoteAudioReference.current.get(targetMemberUserId)?.pause();
    remoteAudioReference.current.delete(targetMemberUserId);
  }, []);

  const createPeer = useCallback((targetMemberUserId: number, isOfferer: boolean) => {
    const existingPeer = peersReference.current.get(targetMemberUserId);
    if (existingPeer) return existingPeer;
    const iceServers = turnCredentialsQuery.data?.iceServers?.flatMap((server) => server.urls ? [{ urls: server.urls, username: server.username, credential: server.credential }] : []) ?? [];
    const peer = new RTCPeerConnection({ iceServers });
    peer.ondatachannel = (event) => bindStatusChannel(targetMemberUserId, event.channel);
    if (isOfferer) {
      bindStatusChannel(targetMemberUserId, peer.createDataChannel(VOICE_STATUS_CHANNEL));
      const transceiver = peer.addTransceiver(AUDIO_KIND, { direction: SEND_AND_RECEIVE });
      void transceiver.sender.replaceTrack(outgoingTrack());
      applyVoiceBitrateCap(peer);
      // An offer sent before the other member finished subscribing is lost; a peer that
      // never connects is torn down so the offer effect sends a fresh one.
      window.setTimeout(() => {
        if (peersReference.current.get(targetMemberUserId) === peer && peer.connectionState !== "connected") {
          closePeer(targetMemberUserId);
          setPeerResetCount((count) => count + 1);
        }
      }, PEER_CONNECT_TIMEOUT_MILLISECONDS);
    }
    peer.onicecandidate = (event) => { if (event.candidate) sendSignal(CANDIDATE_SIGNAL, targetMemberUserId, event.candidate.toJSON()); };
    peer.ontrack = (event) => {
      const remoteStream = event.streams.at(0) ?? new MediaStream([event.track]);
      const audio = remoteAudioReference.current.get(targetMemberUserId) ?? new Audio();
      audio.srcObject = remoteStream;
      audio.muted = isDeafenedReference.current;
      audio.volume = speakerVolumeReference.current / PERCENT;
      const sinkableAudio = audio as HTMLAudioElement & { setSinkId?: (deviceId: string) => Promise<void> };
      if (speakerDeviceId && typeof sinkableAudio.setSinkId === "function") {
        void sinkableAudio.setSinkId(speakerDeviceId).catch(() => {});
      }
      remoteAudioReference.current.set(targetMemberUserId, audio);
      void audio.play().catch(() => {});
    };
    peer.onconnectionstatechange = () => {
      if ((peer.connectionState === "failed" || peer.connectionState === "closed") && peersReference.current.get(targetMemberUserId) === peer) {
        closePeer(targetMemberUserId);
        setPeerResetCount((count) => count + 1);
      }
    };
    peersReference.current.set(targetMemberUserId, peer);
    return peer;
  }, [applyVoiceBitrateCap, bindStatusChannel, closePeer, outgoingTrack, sendSignal, speakerDeviceId, turnCredentialsQuery.data?.iceServers]);

  // A candidate can arrive before the offer or answer it belongs to; it waits here until
  // the peer has a remote description to attach it to.
  const flushPendingCandidates = useCallback(async (targetMemberUserId: number, peer: RTCPeerConnection) => {
    const pendingCandidates = pendingCandidatesReference.current.get(targetMemberUserId) ?? [];
    pendingCandidatesReference.current.delete(targetMemberUserId);
    for (const candidate of pendingCandidates) {
      await peer.addIceCandidate(candidate).catch(() => {});
    }
  }, []);

  const handleSignal = useCallback(async (signal: VoiceSignal) => {
    const payload = JSON.parse(signal.payload) as RTCSessionDescriptionInit | RTCIceCandidateInit;
    const senderUserId = signal.senderUserId;
    if (signal.type === OFFER_SIGNAL) {
      // A fresh offer means the other side restarted its connection, so any existing
      // peer for that member is stale.
      if (peersReference.current.has(senderUserId)) closePeer(senderUserId);
      const peer = createPeer(senderUserId, false);
      await peer.setRemoteDescription(payload as RTCSessionDescriptionInit);
      peer.getTransceivers().forEach((transceiver) => {
        transceiver.direction = SEND_AND_RECEIVE;
        void transceiver.sender.replaceTrack(outgoingTrack());
      });
      applyVoiceBitrateCap(peer);
      const answer = await peer.createAnswer();
      await peer.setLocalDescription(answer);
      sendSignal(ANSWER_SIGNAL, senderUserId, answer);
      await flushPendingCandidates(senderUserId, peer);
      return;
    }
    const existingPeer = peersReference.current.get(senderUserId);
    if (signal.type === ANSWER_SIGNAL && existingPeer) {
      await existingPeer.setRemoteDescription(payload as RTCSessionDescriptionInit);
      await flushPendingCandidates(senderUserId, existingPeer);
      return;
    }
    if (signal.type === CANDIDATE_SIGNAL) {
      if (existingPeer?.remoteDescription) {
        await existingPeer.addIceCandidate(payload as RTCIceCandidateInit).catch(() => {});
      } else {
        const pendingCandidates = pendingCandidatesReference.current.get(senderUserId) ?? [];
        pendingCandidatesReference.current.set(senderUserId, [...pendingCandidates, payload as RTCIceCandidateInit]);
      }
    }
  }, [applyVoiceBitrateCap, closePeer, createPeer, flushPendingCandidates, outgoingTrack, sendSignal]);
  const handleSignalReference = useRef(handleSignal);
  useEffect(() => {
    handleSignalReference.current = handleSignal;
  });

  // The signaling client lives exactly as long as this player is in the room; its
  // handlers read the latest callbacks through refs so a re-render never reconnects it
  // and drops a handshake midway.
  useEffect(() => {
    if (!isInVoice || !currentUserId) return;
    const peers = peersReference.current;
    const statusChannels = statusChannelsReference.current;
    const remoteAudio = remoteAudioReference.current;
    const client = new Client({
      brokerURL: websocketUrl(),
      reconnectDelay: RECONNECT_DELAY_MILLISECONDS,
      onConnect: () => {
        // Someone joined or left: the member list drives who this player offers to,
        // so it has to refresh before a later joiner can be reached.
        client.subscribe(`${VOICE_TOPIC_PREFIX}/${groupId}/voice`, (message) => {
          const event = JSON.parse(message.body) as { type?: string };
          if (event.type === VOICE_PRESENCE_CHANGED_EVENT) onPresenceChangeReference.current?.();
        });
        client.subscribe(`${VOICE_SIGNAL_QUEUE_PREFIX}/${groupId}/voice-signal`, (message) => {
          const signal = JSON.parse(message.body) as Partial<VoiceSignal>;
          if (!signal.type || !signal.senderUserId || !signal.payload) return;
          void handleSignalReference.current(signal as VoiceSignal).catch(() => {});
        });
        // Anyone who joined while this client was still connecting was announced before
        // the subscription above existed, so the member list is re-read once subscribed.
        onPresenceChangeReference.current?.();
        setIsSignalConnected(true);
      },
      onWebSocketClose: () => setIsSignalConnected(false),
      onStompError: () => setIsSignalConnected(false),
    });
    clientReference.current = client;
    client.activate();
    return () => {
      clientReference.current = null;
      setIsSignalConnected(false);
      peers.forEach((peer) => peer.close());
      peers.clear();
      statusChannels.forEach((channel) => channel.close());
      statusChannels.clear();
      setMemberStatuses({});
      remoteAudio.forEach((audio) => audio.pause());
      remoteAudio.clear();
      void client.deactivate();
    };
  }, [currentUserId, groupId, isInVoice]);

  useEffect(() => {
    const activeMemberIds = new Set(voiceMembers.map((member) => member.userId));
    peersReference.current.forEach((_peer, memberUserId) => {
      if (!activeMemberIds.has(memberUserId)) closePeer(memberUserId);
    });
  }, [closePeer, voiceMembers]);

  useEffect(() => {
    if (!isInVoice || !currentUserId || !hasOutgoingAudio || !isSignalConnected) return;
    voiceMembers.forEach(async (member) => {
      const memberUserId = member.userId;
      if (!memberUserId || memberUserId <= currentUserId || peersReference.current.has(memberUserId)) return;
      const peer = createPeer(memberUserId, true);
      const offer = await peer.createOffer();
      await peer.setLocalDescription(offer);
      sendSignal(OFFER_SIGNAL, memberUserId, offer);
    });
  }, [createPeer, currentUserId, hasOutgoingAudio, isInVoice, isSignalConnected, peerResetCount, sendSignal, voiceMembers]);

  // Always leaves the player able to listen: a missing or refused microphone still sets
  // up the outgoing (silent) track, and the result only reports whether talking works.
  const startMicrophone = useCallback(async () => {
    const mixer = ensureMixer();
    if (typeof navigator === "undefined" || !navigator.mediaDevices?.getUserMedia) {
      setMicrophoneError(true);
      setMicrophoneErrorMessage(INSECURE_CONTEXT_MESSAGE);
      refreshOutgoingTrack();
      return false;
    }
    try {
      const microphoneStream = await navigator.mediaDevices.getUserMedia(microphoneConstraints(microphoneDeviceId));
      microphoneStream.getAudioTracks().forEach((track) => { track.contentHint = MICROPHONE_SPEECH_CONTENT_HINT; });
      microphoneStreamReference.current?.getTracks().forEach((track) => track.stop());
      microphoneStreamReference.current = microphoneStream;
      setMicrophoneStream(microphoneStream);
      if (mixer) {
        await connectMicrophoneGraph(mixer, microphoneStream, microphoneVolumeReference.current, noiseGateSensitivityReference.current, isRnnoiseEnabledReference.current);
      }
      refreshOutgoingTrack();
      setMicrophoneError(false);
      setMicrophoneErrorMessage(null);
      return true;
    } catch (error) {
      setMicrophoneError(true);
      setMicrophoneErrorMessage(describeMicrophoneError(error));
      refreshOutgoingTrack();
      return false;
    }
  }, [ensureMixer, microphoneDeviceId, refreshOutgoingTrack]);

  const stopVoice = useCallback(() => {
    microphoneStreamReference.current?.getTracks().forEach((track) => track.stop());
    microphoneStreamReference.current = null;
    setMicrophoneStream(null);
    setIsSpeaking(false);
    statusChannelsReference.current.forEach((channel) => channel.close());
    statusChannelsReference.current.clear();
    setMemberStatuses({});
    peersReference.current.forEach((peer) => peer.close());
    peersReference.current.clear();
    remoteAudioReference.current.forEach((audio) => audio.pause());
    remoteAudioReference.current.clear();
    if (mixerReference.current) disconnectMicrophoneGraph(mixerReference.current);
    void mixerReference.current?.context.close().catch(() => {});
    mixerReference.current = null;
    setHasOutgoingAudio(false);
    setIsMuted(false);
    isDeafenedReference.current = false;
    setIsDeafened(false);
  }, []);

  const toggleMute = useCallback(() => {
    const nextMuted = !isMuted;
    microphoneStreamReference.current?.getAudioTracks().forEach((track) => { track.enabled = !nextMuted; });
    setIsMuted(nextMuted);
  }, [isMuted]);

  const toggleDeafen = useCallback(() => {
    isDeafenedReference.current = !isDeafenedReference.current;
    setIsDeafened(isDeafenedReference.current);
    applyRemoteAudioMuting();
  }, [applyRemoteAudioMuting]);

  return {
    memberStatuses,
    localStatus: { isMuted: isMuted || microphoneError, isDeafened, isSpeaking: isSpeaking && !isMuted && !microphoneError },
    isMuted,
    isDeafened,
    microphoneError,
    microphoneErrorMessage,
    isSignalConnected,
    // True once this browser has opened its audio for the call; the server can still list a
    // member as in the call after a reload or a crash, when this is false.
    isVoiceStarted: hasOutgoingAudio,
    startMicrophone,
    stopVoice,
    toggleMute,
    toggleDeafen,
  };
}
