"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";

import { useGetVoiceTurnCredentials } from "@/hooks/generated/group-management/group-management";
import type { MemberDTO } from "@/hooks/models/memberDTO";

const WEBSOCKET_PATH = "/ws";
const VOICE_TOPIC_PREFIX = "/topic/groups";
const VOICE_SIGNAL_PREFIX = "/app/groups";
const RECONNECT_DELAY_MILLISECONDS = 3_000;
const OFFER_SIGNAL = "OFFER";
const ANSWER_SIGNAL = "ANSWER";
const CANDIDATE_SIGNAL = "CANDIDATE";

interface VoiceSignal { type: string; senderUserId: number; targetMemberUserId: number; payload: string; }

function websocketUrl(): string {
  const apiUrl = new URL(process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080");
  apiUrl.protocol = apiUrl.protocol === "https:" ? "wss:" : "ws:";
  apiUrl.pathname = WEBSOCKET_PATH;
  apiUrl.search = "";
  return apiUrl.toString();
}

export function useVoiceMesh(groupId: number, currentUserId: number | undefined, voiceMembers: MemberDTO[], isInVoice: boolean) {
  const turnCredentialsQuery = useGetVoiceTurnCredentials(groupId, { query: { enabled: isInVoice, retry: false } });
  const clientReference = useRef<Client | null>(null);
  const streamReference = useRef<MediaStream | null>(null);
  const peersReference = useRef(new Map<number, RTCPeerConnection>());
  const [isMuted, setIsMuted] = useState(false);
  const [microphoneError, setMicrophoneError] = useState(false);
  const [isSignalConnected, setIsSignalConnected] = useState(false);

  const sendSignal = useCallback((type: string, targetMemberUserId: number, payload: unknown) => {
    clientReference.current?.publish({ destination: `${VOICE_SIGNAL_PREFIX}/${groupId}/voice/signal`, body: JSON.stringify({ type, targetMemberUserId, payload: JSON.stringify(payload) }) });
  }, [groupId]);

  const createPeer = useCallback((targetMemberUserId: number) => {
    const existingPeer = peersReference.current.get(targetMemberUserId);
    if (existingPeer) return existingPeer;
    const iceServers = turnCredentialsQuery.data?.iceServers?.flatMap((server) => server.urls ? [{ urls: server.urls, username: server.username, credential: server.credential }] : []) ?? [];
    const peer = new RTCPeerConnection({ iceServers });
    streamReference.current?.getTracks().forEach((track) => peer.addTrack(track, streamReference.current!));
    peer.onicecandidate = (event) => { if (event.candidate) sendSignal(CANDIDATE_SIGNAL, targetMemberUserId, event.candidate.toJSON()); };
    peer.ontrack = (event) => { const audio = new Audio(); audio.srcObject = event.streams[0]; void audio.play(); };
    peer.onconnectionstatechange = () => { if (peer.connectionState === "failed" || peer.connectionState === "closed") peersReference.current.delete(targetMemberUserId); };
    peersReference.current.set(targetMemberUserId, peer);
    return peer;
  }, [sendSignal, turnCredentialsQuery.data?.iceServers]);

  useEffect(() => {
    if (!isInVoice || !currentUserId) return;
    const client = new Client({ brokerURL: websocketUrl(), reconnectDelay: RECONNECT_DELAY_MILLISECONDS, onConnect: () => { setIsSignalConnected(true); client.subscribe(`${VOICE_TOPIC_PREFIX}/${groupId}/voice`, async (message) => {
      const signal = JSON.parse(message.body) as Partial<VoiceSignal>;
      if (!signal.type || signal.targetMemberUserId !== currentUserId || !signal.senderUserId || !signal.payload) return;
      const peer = createPeer(signal.senderUserId);
      const payload = JSON.parse(signal.payload) as RTCSessionDescriptionInit | RTCIceCandidateInit;
      if (signal.type === OFFER_SIGNAL) { await peer.setRemoteDescription(payload as RTCSessionDescriptionInit); const answer = await peer.createAnswer(); await peer.setLocalDescription(answer); sendSignal(ANSWER_SIGNAL, signal.senderUserId, answer); }
      if (signal.type === ANSWER_SIGNAL) await peer.setRemoteDescription(payload as RTCSessionDescriptionInit);
      if (signal.type === CANDIDATE_SIGNAL) await peer.addIceCandidate(payload as RTCIceCandidateInit);
    }); }, onWebSocketClose: () => setIsSignalConnected(false), onStompError: () => setIsSignalConnected(false) });
    clientReference.current = client;
    client.activate();
    return () => { clientReference.current = null; setIsSignalConnected(false); void client.deactivate(); };
  }, [createPeer, currentUserId, groupId, isInVoice, sendSignal]);

  useEffect(() => {
    if (!isInVoice || !currentUserId || !streamReference.current || !isSignalConnected) return;
    voiceMembers.filter((member) => member.id && member.id > currentUserId).forEach(async (member) => {
      if (peersReference.current.has(member.id!)) return;
      const peer = createPeer(member.id!);
      const offer = await peer.createOffer();
      await peer.setLocalDescription(offer);
      sendSignal(OFFER_SIGNAL, member.id!, offer);
    });
  }, [createPeer, currentUserId, isInVoice, isSignalConnected, sendSignal, voiceMembers]);

  const startMicrophone = useCallback(async () => { try { streamReference.current = await navigator.mediaDevices.getUserMedia({ audio: true }); setMicrophoneError(false); return true; } catch { setMicrophoneError(true); return false; } }, []);
  const stopMicrophone = useCallback(() => { streamReference.current?.getTracks().forEach((track) => track.stop()); streamReference.current = null; peersReference.current.forEach((peer) => peer.close()); peersReference.current.clear(); setIsMuted(false); }, []);
  const toggleMute = useCallback(() => { const nextMuted = !isMuted; streamReference.current?.getAudioTracks().forEach((track) => { track.enabled = !nextMuted; }); setIsMuted(nextMuted); }, [isMuted]);

  return { isMuted, microphoneError, startMicrophone, stopMicrophone, toggleMute };
}
