"use client";

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { Client } from "@stomp/stompjs";

import { useGetVoiceTurnCredentials } from "@/hooks/generated/group-management/group-management";
import { getSongCaptureStream, getSongCaptureTrack, stopSongCapture, subscribeSongCapture } from "@/lib/song-capture";

const WEBSOCKET_PATH = "/ws";
const SESSION_SIGNAL_PREFIX = "/app/sessions";
const SESSION_SIGNAL_QUEUE_PREFIX = "/user/queue/sessions";
const SONG_SIGNAL_ACTION = "song/signal";
const SONG_SIGNAL_QUEUE = "song-signal";
const RECONNECT_DELAY_MILLISECONDS = 3_000;
const PEER_CONNECT_TIMEOUT_MILLISECONDS = 8_000;
const OFFER_SIGNAL = "OFFER";
const ANSWER_SIGNAL = "ANSWER";
const CANDIDATE_SIGNAL = "CANDIDATE";
const AUDIO_KIND = "audio";
const SEND_AND_RECEIVE = "sendrecv";
const CONNECTED_STATE = "connected";
const FAILED_STATE = "failed";
const CLOSED_STATE = "closed";
const PERCENT = 100;
const DEFAULT_SONG_VOLUME_PERCENT = 100;
const SONG_MAX_PLAYBACK_RATE_HZ = 48_000;
const OPUS_CODEC_NAME = "opus";
const SDP_LINE_SEPARATOR = "\r\n";
const SDP_RTPMAP_PREFIX = "a=rtpmap:";
const SDP_FMTP_PREFIX = "a=fmtp:";
const SDP_FIELD_SEPARATOR = " ";
const SDP_PARAMETER_SEPARATOR = ";";
const SDP_PARAMETER_VALUE_SEPARATOR = "=";

// Every listener pulls its own upstream from the DJ, so the bitrate is paid once per
// listener. Stereo Opus at 128 kbps is where music stops sounding compressed; raising
// this to 192 kbps adds 64 kbps per listener per direction.
export const SONG_BITRATE_BITS_PER_SECOND = 128_000;

interface SongSignal {
  type: string;
  senderUserId: number;
  targetMemberUserId: number;
  payload: string;
}

export interface SongMeshOptions {
  sessionId: number;
  groupId: number | undefined;
  currentUserId: number | undefined;
  // Every player's user id in the active session, including the caller's. The song relay
  // only accepts signals between session players, so this is also the mesh's member list.
  playerUserIds: number[];
  // This user is the DJ for the round in progress and the round's song has not been
  // revealed. The captured track is attached only while this is true.
  isDjForCurrentRound: boolean;
  isSongRevealed: boolean;
  // Percentages. The song's own volume, independent of the voice speaker volume.
  songVolume?: number;
  // The active player's song is cut off from lock-in until the round is scored. This mutes
  // only the incoming song audio, never the DJ's voice.
  isSongSilenced?: boolean;
  // A paused game mutes playback for listeners and detaches the DJ's track so no audio
  // leaks during an interruption.
  isSongPaused?: boolean;
  // Stop conditions, observed rather than queried. A true hasSessionEnded, or an explicit
  // false on either membership flag, stops the capture and detaches the song sender.
  // Undefined means the value is not known yet, so it never stops on its own.
  hasSessionEnded: boolean;
  isPlayerInSession?: boolean;
  hasGroupMembership?: boolean;
}

export interface SongMesh {
  isCapturing: boolean;
  isPlaybackBlocked: boolean;
  resumePlayback: () => void;
  // What the visualizer should analyze: the DJ's own capture while their turn is playing,
  // otherwise the song stream arriving from the current DJ.
  visualizerStream: MediaStream | null;
  isSignalConnected: boolean;
}

function websocketUrl(): string {
  const apiUrl = new URL(process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080");
  apiUrl.protocol = apiUrl.protocol === "https:" ? "wss:" : "ws:";
  apiUrl.pathname = WEBSOCKET_PATH;
  apiUrl.search = "";
  return apiUrl.toString();
}

function opusPayloadTypeFromRtpmap(line: string): string | null {
  if (!line.startsWith(SDP_RTPMAP_PREFIX)) return null;
  const [payloadType, codec] = line.slice(SDP_RTPMAP_PREFIX.length).split(SDP_FIELD_SEPARATOR);
  if (!payloadType || !codec) return null;
  const [codecName] = codec.split("/");
  return codecName.toLowerCase() === OPUS_CODEC_NAME ? payloadType : null;
}

function fmtpPayloadType(line: string): string | null {
  if (!line.startsWith(SDP_FMTP_PREFIX)) return null;
  const [payloadType] = line.slice(SDP_FMTP_PREFIX.length).split(SDP_FIELD_SEPARATOR);
  return payloadType || null;
}

function songOpusParameters(bitrateBitsPerSecond: number): Map<string, string> {
  return new Map([
    ["stereo", "1"],
    ["sprop-stereo", "1"],
    ["useinbandfec", "1"],
    ["maxaveragebitrate", String(bitrateBitsPerSecond)],
    ["maxplaybackrate", String(SONG_MAX_PLAYBACK_RATE_HZ)],
  ]);
}

function serializeOpusFmtpLine(payloadType: string, parameters: Map<string, string>): string {
  const parameterText = [...parameters].map(([name, value]) => `${name}${SDP_PARAMETER_VALUE_SEPARATOR}${value}`).join(SDP_PARAMETER_SEPARATOR);
  return `${SDP_FMTP_PREFIX}${payloadType}${SDP_FIELD_SEPARATOR}${parameterText}`;
}

function mergeOpusFmtpLine(line: string, payloadType: string, bitrateBitsPerSecond: number): string {
  const [, ...parameterTexts] = line.slice(SDP_FMTP_PREFIX.length).split(SDP_FIELD_SEPARATOR);
  const parameters = new Map<string, string>();
  for (const parameter of parameterTexts.join(SDP_FIELD_SEPARATOR).split(SDP_PARAMETER_SEPARATOR)) {
    const [name, value] = parameter.split(SDP_PARAMETER_VALUE_SEPARATOR);
    if (name) parameters.set(name, value ?? "");
  }
  songOpusParameters(bitrateBitsPerSecond).forEach((value, name) => parameters.set(name, value));
  return serializeOpusFmtpLine(payloadType, parameters);
}

// Opus ignores the offered channel count unless the fmtp line asks for stereo, so the
// parameters are written into both the offer and the answer before they are sent.
export function applySongOpusParameters(sdp: string, bitrateBitsPerSecond: number): string {
  const lines = sdp.split(SDP_LINE_SEPARATOR);
  const opusPayloadTypes = new Set<string>();
  for (const line of lines) {
    const payloadType = opusPayloadTypeFromRtpmap(line);
    if (payloadType) opusPayloadTypes.add(payloadType);
  }
  if (opusPayloadTypes.size === 0) return sdp;
  const payloadTypesWithFmtp = new Set<string>();
  for (const line of lines) {
    const payloadType = fmtpPayloadType(line);
    if (payloadType && opusPayloadTypes.has(payloadType)) payloadTypesWithFmtp.add(payloadType);
  }
  const mungedLines: string[] = [];
  for (const line of lines) {
    const fmtpType = fmtpPayloadType(line);
    if (fmtpType && opusPayloadTypes.has(fmtpType)) {
      mungedLines.push(mergeOpusFmtpLine(line, fmtpType, bitrateBitsPerSecond));
      continue;
    }
    mungedLines.push(line);
    const rtpmapType = opusPayloadTypeFromRtpmap(line);
    if (rtpmapType && !payloadTypesWithFmtp.has(rtpmapType)) {
      mungedLines.push(serializeOpusFmtpLine(rtpmapType, songOpusParameters(bitrateBitsPerSecond)));
    }
  }
  return mungedLines.join(SDP_LINE_SEPARATOR);
}

function withSongAudioParameters(bitrateBitsPerSecond: number) {
  return (description: RTCSessionDescriptionInit): RTCSessionDescriptionInit => ({
    type: description.type,
    sdp: applySongOpusParameters(description.sdp ?? "", bitrateBitsPerSecond),
  });
}

async function applySenderBitrate(sender: RTCRtpSender, maxBitrateBitsPerSecond: number) {
  try {
    const parameters = sender.getParameters();
    if (parameters.encodings.length === 0) {
      parameters.encodings = [{}];
    }
    parameters.encodings.forEach((encoding) => {
      encoding.maxBitrate = maxBitrateBitsPerSecond;
    });
    await sender.setParameters(parameters);
  } catch {
    // A sender can refuse parameters before negotiation; the SDP ceiling still applies.
  }
}

// A second mesh, separate from voice: one peer connection per pair of session players
// carrying a single audio transceiver. Every player joins as a listener the moment the
// session is known, with no microphone and no click. Each DJ's share starts once on the
// audio check and runs for the whole match; its track is swapped into the transceivers
// with replaceTrack only while that player's DJ turn plays and the song is hidden, and
// out again afterwards, so the connections stay up and changing who is sending never
// renegotiates. The lower user id offers, matching the voice mesh.
export function useSongMesh(options: SongMeshOptions): SongMesh {
  const {
    sessionId,
    groupId,
    currentUserId,
    playerUserIds,
    isDjForCurrentRound,
    isSongRevealed,
    songVolume = DEFAULT_SONG_VOLUME_PERCENT,
    isSongSilenced = false,
    isSongPaused = false,
    hasSessionEnded,
    isPlayerInSession,
    hasGroupMembership,
  } = options;

  const [isPlaybackBlocked, setIsPlaybackBlocked] = useState(false);
  const [isSignalConnected, setIsSignalConnected] = useState(false);
  const [peerResetCount, setPeerResetCount] = useState(0);
  const [activeRemoteStream, setActiveRemoteStream] = useState<MediaStream | null>(null);

  const capturedStream = useSyncExternalStore(subscribeSongCapture, getSongCaptureStream, () => null);
  const [capturedTrack] = capturedStream?.getAudioTracks() ?? [];
  const shouldSendSong = isDjForCurrentRound && !isSongRevealed && !isSongPaused;

  const clientReference = useRef<Client | null>(null);
  const peersReference = useRef(new Map<number, RTCPeerConnection>());
  const pendingCandidatesReference = useRef(new Map<number, RTCIceCandidateInit[]>());
  const remoteAudioReference = useRef(new Map<number, HTMLAudioElement>());
  const activeRemoteUserIdReference = useRef<number | undefined>(undefined);
  const isSongActiveReference = useRef(false);
  const songVolumeReference = useRef(songVolume);
  const isSongSilencedReference = useRef(isSongSilenced);
  const playerUserIdsReference = useRef<number[]>(playerUserIds);

  useEffect(() => {
    playerUserIdsReference.current = playerUserIds;
  });

  useEffect(() => {
    songVolumeReference.current = songVolume;
    remoteAudioReference.current.forEach((audio) => {
      audio.volume = songVolume / PERCENT;
    });
  }, [songVolume]);

  useEffect(() => {
    isSongSilencedReference.current = isSongSilenced || isSongPaused;
    remoteAudioReference.current.forEach((audio) => {
      audio.muted = isSongSilenced || isSongPaused;
    });
  }, [isSongPaused, isSongSilenced]);

  const hasValidSessionId = Number.isInteger(sessionId) && sessionId > 0;
  const isSessionOpen = !hasSessionEnded && isPlayerInSession !== false && hasGroupMembership !== false;
  const isMeshActive = hasValidSessionId && currentUserId !== undefined && isSessionOpen;

  const turnCredentialsQuery = useGetVoiceTurnCredentials(groupId ?? 0, {
    query: { enabled: Boolean(groupId) && isMeshActive, retry: false },
  });

  const getTrackForSending = useCallback((): MediaStreamTrack | null => {
    return isSongActiveReference.current ? getSongCaptureTrack() : null;
  }, []);

  const sendSignal = useCallback((type: string, targetMemberUserId: number, payload: unknown) => {
    const client = clientReference.current;
    if (!client?.connected) return;
    client.publish({
      destination: `${SESSION_SIGNAL_PREFIX}/${sessionId}/${SONG_SIGNAL_ACTION}`,
      body: JSON.stringify({ type, targetMemberUserId, payload: JSON.stringify(payload) }),
    });
  }, [sessionId]);

  const closePeer = useCallback((targetMemberUserId: number) => {
    peersReference.current.get(targetMemberUserId)?.close();
    peersReference.current.delete(targetMemberUserId);
    pendingCandidatesReference.current.delete(targetMemberUserId);
    remoteAudioReference.current.get(targetMemberUserId)?.pause();
    remoteAudioReference.current.delete(targetMemberUserId);
    if (activeRemoteUserIdReference.current === targetMemberUserId) {
      activeRemoteUserIdReference.current = undefined;
      setActiveRemoteStream(null);
    }
  }, []);

  // The DJ is the only sender with a live track, so the first remote track that unmutes
  // is the one the visualizer should follow; every other peer stays silent.
  const bindRemoteTrack = useCallback((targetMemberUserId: number, event: RTCTrackEvent) => {
    const [firstStream] = event.streams;
    const remoteStream = firstStream ?? new MediaStream([event.track]);
    const audio = remoteAudioReference.current.get(targetMemberUserId) ?? new Audio();
    audio.srcObject = remoteStream;
    audio.volume = songVolumeReference.current / PERCENT;
    audio.muted = isSongSilencedReference.current;
    remoteAudioReference.current.set(targetMemberUserId, audio);
    const markActive = () => {
      activeRemoteUserIdReference.current = targetMemberUserId;
      setActiveRemoteStream(remoteStream);
    };
    const clearActive = () => {
      if (activeRemoteUserIdReference.current !== targetMemberUserId) return;
      activeRemoteUserIdReference.current = undefined;
      setActiveRemoteStream(null);
    };
    event.track.addEventListener("unmute", markActive);
    event.track.addEventListener("mute", clearActive);
    if (!event.track.muted) markActive();
    void audio.play().catch(() => setIsPlaybackBlocked(true));
  }, []);

  const createPeer = useCallback((targetMemberUserId: number, isOfferer: boolean) => {
    const existingPeer = peersReference.current.get(targetMemberUserId);
    if (existingPeer) return existingPeer;
    const iceServers = turnCredentialsQuery.data?.iceServers?.flatMap((server) => server.urls ? [{ urls: server.urls, username: server.username, credential: server.credential }] : []) ?? [];
    const peer = new RTCPeerConnection({ iceServers });
    if (isOfferer) {
      const transceiver = peer.addTransceiver(AUDIO_KIND, { direction: SEND_AND_RECEIVE });
      void transceiver.sender.replaceTrack(getTrackForSending());
      void applySenderBitrate(transceiver.sender, SONG_BITRATE_BITS_PER_SECOND);
      // An offer sent before the other member finished subscribing is lost; a peer that
      // never connects is torn down so the offer effect sends a fresh one.
      window.setTimeout(() => {
        if (peersReference.current.get(targetMemberUserId) === peer && peer.connectionState !== CONNECTED_STATE) {
          closePeer(targetMemberUserId);
          setPeerResetCount((count) => count + 1);
        }
      }, PEER_CONNECT_TIMEOUT_MILLISECONDS);
    }
    peer.onicecandidate = (event) => { if (event.candidate) sendSignal(CANDIDATE_SIGNAL, targetMemberUserId, event.candidate.toJSON()); };
    peer.ontrack = (event) => bindRemoteTrack(targetMemberUserId, event);
    peer.onconnectionstatechange = () => {
      if ((peer.connectionState === FAILED_STATE || peer.connectionState === CLOSED_STATE) && peersReference.current.get(targetMemberUserId) === peer) {
        closePeer(targetMemberUserId);
        setPeerResetCount((count) => count + 1);
      }
    };
    peersReference.current.set(targetMemberUserId, peer);
    return peer;
  }, [bindRemoteTrack, closePeer, getTrackForSending, sendSignal, turnCredentialsQuery.data?.iceServers]);

  // A candidate can arrive before the offer or answer it belongs to; it waits here until
  // the peer has a remote description to attach it to.
  const flushPendingCandidates = useCallback(async (targetMemberUserId: number, peer: RTCPeerConnection) => {
    const pendingCandidates = pendingCandidatesReference.current.get(targetMemberUserId) ?? [];
    pendingCandidatesReference.current.delete(targetMemberUserId);
    for (const candidate of pendingCandidates) {
      await peer.addIceCandidate(candidate).catch(() => {});
    }
  }, []);

  const mungeDescription = useMemo(() => withSongAudioParameters(SONG_BITRATE_BITS_PER_SECOND), []);

  const handleSignal = useCallback(async (signal: SongSignal) => {
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
        void transceiver.sender.replaceTrack(getTrackForSending());
        void applySenderBitrate(transceiver.sender, SONG_BITRATE_BITS_PER_SECOND);
      });
      const answer = await peer.createAnswer();
      const mungedAnswer = mungeDescription(answer);
      await peer.setLocalDescription(mungedAnswer);
      sendSignal(ANSWER_SIGNAL, senderUserId, mungedAnswer);
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
  }, [closePeer, createPeer, flushPendingCandidates, getTrackForSending, mungeDescription, sendSignal]);
  const handleSignalReference = useRef(handleSignal);
  useEffect(() => {
    handleSignalReference.current = handleSignal;
  });

  // The signaling client lives exactly as long as this player is in the session; its
  // handlers read the latest callbacks through refs so a re-render never reconnects it
  // and drops a handshake midway.
  useEffect(() => {
    if (!isMeshActive) return;
    const peers = peersReference.current;
    const remoteAudio = remoteAudioReference.current;
    const client = new Client({
      brokerURL: websocketUrl(),
      reconnectDelay: RECONNECT_DELAY_MILLISECONDS,
      onConnect: () => {
        client.subscribe(`${SESSION_SIGNAL_QUEUE_PREFIX}/${sessionId}/${SONG_SIGNAL_QUEUE}`, (message) => {
          const signal = JSON.parse(message.body) as Partial<SongSignal>;
          if (!signal.type || !signal.senderUserId || !signal.payload) return;
          void handleSignalReference.current(signal as SongSignal).catch(() => {});
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
      peers.forEach((peer) => {
        peer.getTransceivers().forEach((transceiver) => {
          void transceiver.sender.replaceTrack(null);
        });
        peer.close();
      });
      peers.clear();
      remoteAudio.forEach((audio) => audio.pause());
      remoteAudio.clear();
      activeRemoteUserIdReference.current = undefined;
      setActiveRemoteStream(null);
    };
  }, [isMeshActive, sessionId]);

  const playerIdsKey = useMemo(() => [...playerUserIds].sort((first, second) => first - second).join(","), [playerUserIds]);

  useEffect(() => {
    const activeMemberIds = new Set(playerUserIdsReference.current);
    peersReference.current.forEach((_peer, memberUserId) => {
      if (!activeMemberIds.has(memberUserId)) closePeer(memberUserId);
    });
  }, [closePeer, playerIdsKey]);

  const offerToMember = useCallback(async (memberUserId: number) => {
    const peer = createPeer(memberUserId, true);
    const offer = await peer.createOffer();
    const mungedOffer = mungeDescription(offer);
    await peer.setLocalDescription(mungedOffer);
    sendSignal(OFFER_SIGNAL, memberUserId, mungedOffer);
  }, [createPeer, mungeDescription, sendSignal]);

  useEffect(() => {
    if (!isMeshActive || !isSignalConnected || currentUserId === undefined) return;
    playerUserIdsReference.current.forEach((memberUserId) => {
      if (!memberUserId || memberUserId <= currentUserId || peersReference.current.has(memberUserId)) return;
      void offerToMember(memberUserId).catch(() => {});
    });
  }, [currentUserId, isMeshActive, isSignalConnected, offerToMember, peerResetCount, playerIdsKey]);

  // Attaching runs as soon as this player becomes the DJ for the round, which is before
  // the DJ opens YouTube and starts playback, so the sender is warm before audio flows.
  useEffect(() => {
    isSongActiveReference.current = shouldSendSong;
    const track = shouldSendSong ? capturedTrack ?? null : null;
    peersReference.current.forEach((peer) => {
      peer.getTransceivers().forEach((transceiver) => {
        void transceiver.sender.replaceTrack(track);
      });
    });
  }, [capturedTrack, shouldSendSong]);

  // The capture stops on its own for a stopped share bar or a page unload; the rest of
  // the stop conditions arrive as options, because a route change must not end it. The
  // mesh teardown above detaches the song sender for the same conditions.
  useEffect(() => {
    if (isSessionOpen) return;
    stopSongCapture();
  }, [isSessionOpen]);

  const resumePlayback = useCallback(() => {
    const remoteAudio = [...remoteAudioReference.current.values()];
    void Promise.allSettled(remoteAudio.map((audio) => audio.play())).then((results) => {
      setIsPlaybackBlocked(results.some((result) => result.status === "rejected"));
    });
  }, []);

  return {
    isCapturing: capturedStream !== null,
    isPlaybackBlocked,
    resumePlayback,
    visualizerStream: shouldSendSong ? capturedStream : activeRemoteStream,
    isSignalConnected,
  };
}

