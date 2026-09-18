"use client";

import { useRef, useState } from "react";
import { Headphones, Loader2, Mic, MicOff, Phone, PhoneOff } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";

import { getGetActiveMembershipQueryKey, getGetGroupQueryKey, useGetActiveMembership, useJoinVoice, useLeaveVoice } from "@/hooks/generated/group-management/group-management";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";

const MEMBER_COLORS = ["bg-success", "bg-info", "bg-primary", "bg-accent", "bg-warning", "bg-pink"];

export function AppVoiceSidebar() {
  const queryClient = useQueryClient();
  const activeMembershipQuery = useGetActiveMembership({ query: { retry: false } });
  const currentUserQuery = useGetCurrentUser();
  const joinVoice = useJoinVoice();
  const leaveVoice = useLeaveVoice();
  const mediaStreamReference = useRef<MediaStream | null>(null);
  const [isMuted, setIsMuted] = useState(false);
  const [microphoneError, setMicrophoneError] = useState(false);
  const activeGroup = activeMembershipQuery.data;
  const groupId = activeGroup?.id;
  const currentMember = activeGroup?.members?.find((member) => member.id === currentUserQuery.data?.id);
  const isInVoice = Boolean(currentMember?.isInVoice);
  const voiceMembers = (activeGroup?.members ?? []).filter((member) => member.isInVoice);

  function refreshVoicePresence() {
    if (groupId) void queryClient.invalidateQueries({ queryKey: getGetGroupQueryKey(groupId) });
    void queryClient.invalidateQueries({ queryKey: getGetActiveMembershipQueryKey() });
  }

  async function joinCall() {
    if (!groupId) return;
    try {
      mediaStreamReference.current = await navigator.mediaDevices.getUserMedia({ audio: true });
      setMicrophoneError(false);
      joinVoice.mutate({ groupId }, { onSuccess: refreshVoicePresence });
    } catch {
      setMicrophoneError(true);
    }
  }

  function leaveCall() {
    if (!groupId) return;
    mediaStreamReference.current?.getTracks().forEach((track) => track.stop());
    mediaStreamReference.current = null;
    leaveVoice.mutate({ groupId }, { onSuccess: refreshVoicePresence });
  }

  function toggleMute() {
    const stream = mediaStreamReference.current;
    if (!stream) return;
    const nextMuted = !isMuted;
    stream.getAudioTracks().forEach((track) => { track.enabled = !nextMuted; });
    setIsMuted(nextMuted);
  }

  if (!activeGroup) return null;

  return <aside className="flex w-[100px] shrink-0 flex-col items-center border-l-[3px] border-sidebar-border bg-sidebar/80 px-2 py-[22px]"><div className="flex flex-col items-center gap-6">{voiceMembers.map((member, index) => <div key={member.id} className="flex flex-col items-center gap-2"><div className={`flex size-[52px] items-center justify-center rounded-full font-display text-base text-primary-foreground ${MEMBER_COLORS[index % MEMBER_COLORS.length]}`}>{member.displayName?.charAt(0).toUpperCase() ?? "?"}</div><span className="max-w-[84px] truncate text-[10px] text-sidebar-foreground">{member.displayName ?? "Player"}</span></div>)}</div><div className="flex-1" />{microphoneError ? <p className="mb-2 text-center text-[9px] text-destructive">Microphone permission is needed.</p> : null}<div className="flex flex-col items-center gap-2.5">{isInVoice ? <><button type="button" title="Mute" onClick={toggleMute} className="flex size-10 items-center justify-center rounded-xl bg-card text-card-foreground">{isMuted ? <MicOff className="size-[19px]" /> : <Mic className="size-[19px]" />}</button><button type="button" title="Deafen" className="flex size-10 items-center justify-center rounded-xl bg-card text-card-foreground"><Headphones className="size-[19px]" /></button><div className="my-1 h-0.5 w-8 bg-sidebar-border" /><button type="button" title="Leave voice" onClick={leaveCall} disabled={leaveVoice.isPending} className="flex size-10 items-center justify-center rounded-xl bg-destructive text-destructive-foreground">{leaveVoice.isPending ? <Loader2 className="size-4 animate-spin" /> : <PhoneOff className="size-[19px]" />}</button></> : <button type="button" title="Join call" onClick={joinCall} disabled={joinVoice.isPending} className="flex size-10 items-center justify-center rounded-full border-2 border-dashed border-green text-green">{joinVoice.isPending ? <Loader2 className="size-4 animate-spin" /> : <Phone className="size-[19px]" />}</button>}</div></aside>;
}
