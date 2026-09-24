"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";

import { useGetVoiceTurnCredentials } from "@/hooks/generated/group-management/group-management";
import type { MemberDTO } from "@/hooks/models/memberDTO";
import { publishLocalAudioStream, publishTabAudioSharing } from "./use-local-audio-stream";

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
const SEND_AND_RECEIVE = "sendrecv";
const NOT_ALLOWED_ERROR_NAME = "NotAllowedError";
const NOT_FOUND_ERROR_NAME = "NotFoundError";
const OVERCONSTRAINED_ERROR_NAME = "OverconstrainedError";
const INSECURE_CONTEXT_MESSAGE = "Voice chat needs HTTPS or localhost to use a microphone. You can still listen.";
const MICROPHONE_PERMISSION_MESSAGE = "Microphone permission is needed to talk. You can still listen.";
const MICROPHONE_NOT_FOUND_MESSAGE = "No microphone was found. You can still listen.";
const MICROPHONE_START_FAILED_MESSAGE = "Could not start the microphone. You can still listen.";
const TAB_AUDIO_UNSUPPORTED_MESSAGE = "This browser can't share tab audio.";
const TAB_AUDIO_MISSING_MESSAGE = "No audio was shared. Pick the YouTube tab and turn on tab audio.";
const TAB_AUDIO_FAILED_MESSAGE = "Couldn't share tab audio.";

interface VoiceSignal { type: string; senderUserId: number; targetMemberUserId: number; payload: string; }

interface AudioMixer {
  context: AudioContext;
  destination: MediaStreamAudioDestinationNode;
  microphoneSource: MediaStreamAudioSourceNode | null;
  tabSource: MediaStreamAudioSourceNode | null;
}

interface AudioContextWindow extends Window {
  webkitAudioContext?: typeof AudioContext;
}

// Chromium-only display-media hints: exclude the game tab itself from the picker and
// offer system audio, so the DJ picks the YouTube tab (or the whole system's audio).
interface DisplayAudioConstraints extends DisplayMediaStreamOptions {
  selfBrowserSurface?: "exclude" | "include";
  systemAudio?: "exclude" | "include";
}

const PLACEMENT_LOCKED_STATUSES = new Set(["COUNTDOWN", "BETTING", "REVEALED", "SCORED"]);

// The active player's song audio cuts off from lock-in until the next round starts;
// only the DJ's incoming audio is silenced, so voice chat with everyone else goes on.
export function shouldSilenceDjForActivePlayer(
  round: { activePlayerId?: number; status?: string } | undefined,
  currentPlayerId: number | undefined,
): boolean {
  if (!round || currentPlayerId === undefined || round.activePlayerId !== currentPlayerId) return false;
  return PLACEMENT_LOCKED_STATUSES.has(round.status ?? "");
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
  return { context, destination: context.createMediaStreamDestination(), microphoneSource: null, tabSource: null };
}

interface VoiceMeshOptions {
  microphoneDeviceId?: string;
  speakerDeviceId?: string;
  onPresenceChange?: () => void;
}

// A full mesh of peer connections, one per other voice member. Every peer carries a
// single outgoing audio track: the mix of this player's microphone and, for a DJ, the
// shared YouTube tab audio. Mixing keeps the track stable, so starting or stopping
// either input never needs a renegotiation, and a player with no microphone still
// joins as a listener.
export function useVoiceMesh(groupId: number, currentUserId: number | undefined, voiceMembers: MemberDTO[], isInVoice: boolean, options: VoiceMeshOptions = {}) {
  const { microphoneDeviceId, speakerDeviceId, onPresenceChange } = options;
  const onPresenceChangeReference = useRef(onPresenceChange);
  useEffect(() => {
    onPresenceChangeReference.current = onPresenceChange;
  });
  const turnCredentialsQuery = useGetVoiceTurnCredentials(groupId, { query: { enabled: isInVoice, retry: false } });
  const clientReference = useRef<Client | null>(null);
  const mixerReference = useRef<AudioMixer | null>(null);
  const microphoneStreamReference = useRef<MediaStream | null>(null);
  const tabStreamReference = useRef<MediaStream | null>(null);
  const peersReference = useRef(new Map<number, RTCPeerConnection>());
  const pendingCandidatesReference = useRef(new Map<number, RTCIceCandidateInit[]>());
  const remoteAudioReference = useRef(new Map<number, HTMLAudioElement>());
  const isDeafenedReference = useRef(false);
  const silencedUserIdsReference = useRef(new Set<number>());
  const [isMuted, setIsMuted] = useState(false);
  const [isDeafened, setIsDeafened] = useState(false);
  const [microphoneError, setMicrophoneError] = useState(false);
  const [microphoneErrorMessage, setMicrophoneErrorMessage] = useState<string | null>(null);
  const [tabAudioErrorMessage, setTabAudioErrorMessage] = useState<string | null>(null);
  const [isSharingTabAudio, setIsSharingTabAudio] = useState(false);
  const [isSignalConnected, setIsSignalConnected] = useState(false);
  const [hasOutgoingAudio, setHasOutgoingAudio] = useState(false);
  const [peerResetCount, setPeerResetCount] = useState(0);

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

  const refreshOutgoingTrack = useCallback(() => {
    const track = outgoingTrack();
    setHasOutgoingAudio(true);
    peersReference.current.forEach((peer) => {
      peer.getTransceivers().forEach((transceiver) => { void transceiver.sender.replaceTrack(track); });
    });
  }, [outgoingTrack]);

  const applyRemoteAudioMuting = useCallback(() => {
    remoteAudioReference.current.forEach((audio, userId) => {
      audio.muted = isDeafenedReference.current || silencedUserIdsReference.current.has(userId);
    });
  }, []);

  const sendSignal = useCallback((type: string, targetMemberUserId: number, payload: unknown) => {
    if (!clientReference.current?.connected) return;
    clientReference.current.publish({ destination: `${VOICE_SIGNAL_PREFIX}/${groupId}/voice/signal`, body: JSON.stringify({ type, targetMemberUserId, payload: JSON.stringify(payload) }) });
  }, [groupId]);

  const closePeer = useCallback((targetMemberUserId: number) => {
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
    if (isOfferer) {
      const transceiver = peer.addTransceiver(AUDIO_KIND, { direction: SEND_AND_RECEIVE });
      void transceiver.sender.replaceTrack(outgoingTrack());
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
      audio.muted = isDeafenedReference.current || silencedUserIdsReference.current.has(targetMemberUserId);
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
  }, [closePeer, outgoingTrack, sendSignal, speakerDeviceId, turnCredentialsQuery.data?.iceServers]);

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
  }, [closePeer, createPeer, flushPendingCandidates, outgoingTrack, sendSignal]);
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
      void client.deactivate();
    };
  }, [currentUserId, groupId, isInVoice]);

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

  const publishVisualizerStream = useCallback(() => {
    publishLocalAudioStream(tabStreamReference.current ?? microphoneStreamReference.current);
  }, []);

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
      const microphoneStream = await navigator.mediaDevices.getUserMedia({ audio: microphoneDeviceId ? { deviceId: { exact: microphoneDeviceId } } : true });
      microphoneStreamReference.current?.getTracks().forEach((track) => track.stop());
      microphoneStreamReference.current = microphoneStream;
      if (mixer) {
        mixer.microphoneSource?.disconnect();
        mixer.microphoneSource = mixer.context.createMediaStreamSource(microphoneStream);
        mixer.microphoneSource.connect(mixer.destination);
      }
      publishVisualizerStream();
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
  }, [ensureMixer, microphoneDeviceId, publishVisualizerStream, refreshOutgoingTrack]);

  const stopTabAudio = useCallback(() => {
    tabStreamReference.current?.getTracks().forEach((track) => track.stop());
    tabStreamReference.current = null;
    mixerReference.current?.tabSource?.disconnect();
    if (mixerReference.current) mixerReference.current.tabSource = null;
    setIsSharingTabAudio(false);
    publishTabAudioSharing(false);
    publishVisualizerStream();
  }, [publishVisualizerStream]);

  // Must run straight from a click: getDisplayMedia needs the click's user activation,
  // so nothing is awaited before it.
  const startTabAudio = useCallback(async () => {
    if (typeof navigator === "undefined" || !navigator.mediaDevices?.getDisplayMedia) {
      setTabAudioErrorMessage(TAB_AUDIO_UNSUPPORTED_MESSAGE);
      return false;
    }
    const constraints: DisplayAudioConstraints = { video: true, audio: true, selfBrowserSurface: "exclude", systemAudio: "include" };
    const displayRequest = navigator.mediaDevices.getDisplayMedia(constraints);
    const mixer = ensureMixer();
    try {
      const displayStream = await displayRequest;
      const audioTrack = displayStream.getAudioTracks().at(0);
      displayStream.getVideoTracks().forEach((track) => track.stop());
      if (!audioTrack) {
        displayStream.getTracks().forEach((track) => track.stop());
        setTabAudioErrorMessage(TAB_AUDIO_MISSING_MESSAGE);
        return false;
      }
      stopTabAudio();
      const tabStream = new MediaStream([audioTrack]);
      tabStreamReference.current = tabStream;
      if (mixer) {
        mixer.tabSource = mixer.context.createMediaStreamSource(tabStream);
        mixer.tabSource.connect(mixer.destination);
      } else {
        microphoneStreamReference.current = tabStream;
      }
      audioTrack.addEventListener("ended", stopTabAudio, { once: true });
      publishVisualizerStream();
      refreshOutgoingTrack();
      setTabAudioErrorMessage(null);
      setIsSharingTabAudio(true);
      publishTabAudioSharing(true);
      return true;
    } catch {
      setTabAudioErrorMessage(TAB_AUDIO_FAILED_MESSAGE);
      return false;
    }
  }, [ensureMixer, publishVisualizerStream, refreshOutgoingTrack, stopTabAudio]);

  const stopVoice = useCallback(() => {
    stopTabAudio();
    microphoneStreamReference.current?.getTracks().forEach((track) => track.stop());
    microphoneStreamReference.current = null;
    publishLocalAudioStream(null);
    peersReference.current.forEach((peer) => peer.close());
    peersReference.current.clear();
    remoteAudioReference.current.forEach((audio) => audio.pause());
    remoteAudioReference.current.clear();
    void mixerReference.current?.context.close().catch(() => {});
    mixerReference.current = null;
    setHasOutgoingAudio(false);
    setIsMuted(false);
    isDeafenedReference.current = false;
    setIsDeafened(false);
  }, [stopTabAudio]);

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

  // Silences specific members' incoming audio without touching the connection, used to
  // cut the DJ's song off for the active player once their guess locks in.
  const setSilencedUserIds = useCallback((userIds: number[]) => {
    silencedUserIdsReference.current = new Set(userIds);
    applyRemoteAudioMuting();
  }, [applyRemoteAudioMuting]);

  return {
    isMuted,
    isDeafened,
    microphoneError,
    microphoneErrorMessage,
    tabAudioError: tabAudioErrorMessage !== null,
    tabAudioErrorMessage,
    isSharingTabAudio,
    startMicrophone,
    startTabAudio,
    stopTabAudio,
    stopVoice,
    toggleMute,
    toggleDeafen,
    setSilencedUserIds,
  };
}
