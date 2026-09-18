"use client";

import { useState } from "react";
import { Headphones, Loader2, Mic, MicOff, Phone, PhoneOff } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";

import { getGetActiveMembershipQueryKey, getGetGroupQueryKey, useGetActiveMembership, useJoinVoice, useLeaveVoice } from "@/hooks/generated/group-management/group-management";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import { useVoiceMesh } from "@/hooks/use-voice-mesh";

const MEMBER_COLORS = ["bg-success", "bg-info", "bg-primary", "bg-accent", "bg-warning", "bg-pink"];

export function AppVoiceSidebar() {
  const queryClient = useQueryClient();
  const activeMembershipQuery = useGetActiveMembership({ query: { retry: false } });
  const currentUserQuery = useGetCurrentUser();
  const joinVoice = useJoinVoice();
  const leaveVoice = useLeaveVoice();
  const [microphoneError, setMicrophoneError] = useState(false);
  const activeGroup = activeMembershipQuery.data;
  const groupId = activeGroup?.id;
  const currentMember = activeGroup?.members?.find((member) => member.id === currentUserQuery.data?.id);
  const isInVoice = Boolean(currentMember?.isInVoice);
  const voiceMembers = (activeGroup?.members ?? []).filter((member) => member.isInVoice);
  const voiceMesh = useVoiceMesh(groupId ?? 0, currentUserQuery.data?.id, voiceMembers, isInVoice);

  function refreshVoicePresence() {
    if (groupId) void queryClient.invalidateQueries({ queryKey: getGetGroupQueryKey(groupId) });
    void queryClient.invalidateQueries({ queryKey: getGetActiveMembershipQueryKey() });
  }

  async function joinCall() {
    if (!groupId) return;
    try {
      const hasMicrophone = await voiceMesh.startMicrophone();
      if (!hasMicrophone) return;
      setMicrophoneError(false);
      joinVoice.mutate({ groupId }, { onSuccess: refreshVoicePresence });
    } catch {
      setMicrophoneError(true);
    }
  }

  function leaveCall() {
    if (!groupId) return;
    voiceMesh.stopMicrophone();
    leaveVoice.mutate({ groupId }, { onSuccess: refreshVoicePresence });
  }

  function toggleMute() {
    voiceMesh.toggleMute();
  }

  if (!activeGroup) return null;

  return <aside className="flex w-[100px] shrink-0 flex-col items-center border-l-[3px] border-sidebar-border bg-sidebar/80 px-2 py-[22px]"><div className="flex flex-col items-center gap-6">{voiceMembers.map((member, index) => <div key={member.id} className="flex flex-col items-center gap-2"><div className={`flex size-[52px] items-center justify-center rounded-full font-display text-base text-primary-foreground ${MEMBER_COLORS[index % MEMBER_COLORS.length]}`}>{member.displayName?.charAt(0).toUpperCase() ?? "?"}</div><span className="max-w-[84px] truncate text-[10px] text-sidebar-foreground">{member.displayName ?? "Player"}</span></div>)}</div><div className="flex-1" />{microphoneError || voiceMesh.microphoneError ? <p className="mb-2 text-center text-[9px] text-destructive">Microphone permission is needed.</p> : null}<div className="flex flex-col items-center gap-2.5">{isInVoice ? <><button type="button" title="Mute" onClick={toggleMute} className="flex size-10 items-center justify-center rounded-xl bg-card text-card-foreground">{voiceMesh.isMuted ? <MicOff className="size-[19px]" /> : <Mic className="size-[19px]" />}</button><button type="button" title="Deafen" className="flex size-10 items-center justify-center rounded-xl bg-card text-card-foreground"><Headphones className="size-[19px]" /></button><div className="my-1 h-0.5 w-8 bg-sidebar-border" /><button type="button" title="Leave voice" onClick={leaveCall} disabled={leaveVoice.isPending} className="flex size-10 items-center justify-center rounded-xl bg-destructive text-destructive-foreground">{leaveVoice.isPending ? <Loader2 className="size-4 animate-spin" /> : <PhoneOff className="size-[19px]" />}</button></> : <button type="button" title="Join call" onClick={joinCall} disabled={joinVoice.isPending} className="flex size-10 items-center justify-center rounded-full border-2 border-dashed border-green text-green">{joinVoice.isPending ? <Loader2 className="size-4 animate-spin" /> : <Phone className="size-[19px]" />}</button>}</div></aside>;
}
