"use client";

import { useCallback, useEffect, useState } from "react";
import { Headphones, Loader2, Mic, MicOff, Phone, PhoneOff } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";

import { getGetActiveMembershipQueryKey, getGetGroupQueryKey, useGetActiveMembership, useJoinVoice, useLeaveVoice } from "@/hooks/generated/group-management/group-management";
import { useGetActiveSessionForGroup, useGetSession } from "@/hooks/generated/game-session/game-session";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import { useGameSessionRealtime } from "@/hooks/use-game-session-realtime";
import { shouldCutoffAudioStream, useVoiceMesh } from "@/hooks/use-voice-mesh";
import { Button } from "@/components/shadcn/button";

const MEMBER_COLORS = ["bg-primary", "bg-accent", "bg-warning", "bg-secondary", "bg-primary", "bg-accent"];

export function AppVoiceSidebar() {
  const queryClient = useQueryClient();
  const activeMembershipQuery = useGetActiveMembership({ query: { retry: false } });
  const currentUserQuery = useGetCurrentUser();
  const joinVoice = useJoinVoice();
  const leaveVoice = useLeaveVoice();
  const [microphoneError, setMicrophoneError] = useState(false);
  const activeGroup = activeMembershipQuery.data;
  const groupId = activeGroup?.id;
  const activeSessionQuery = useGetActiveSessionForGroup(groupId ?? 0, { query: { enabled: groupId !== undefined, retry: false } });
  const activeSessionId = activeSessionQuery.data?.id;
  const sessionQuery = useGetSession(activeSessionId ?? 0, { query: { enabled: activeSessionId !== undefined, retry: false } });
  const currentMember = activeGroup?.members?.find((member) => member.id === currentUserQuery.data?.id);
  const isInVoice = Boolean(currentMember?.isInVoice);
  const voiceMembers = (activeGroup?.members ?? []).filter((member) => member.isInVoice);
  const voiceMesh = useVoiceMesh(groupId ?? 0, currentUserQuery.data?.id, voiceMembers, isInVoice);
  const { startTabAudio, stopMicrophone } = voiceMesh;

  useEffect(() => {
    function shareDjTabAudio() {
      if (isInVoice) {
        void startTabAudio();
      }
    }
    window.addEventListener("session-start-audio-share", shareDjTabAudio);
    return () => window.removeEventListener("session-start-audio-share", shareDjTabAudio);
  }, [isInVoice, startTabAudio]);

  const currentPlayerId = sessionQuery.data?.players?.find(
    (player) => player.userId === currentUserQuery.data?.id,
  )?.id;

  const handleRoundEvent = useCallback((event: { type: string; payload?: { activePlayerId?: number } }) => {
    if (shouldCutoffAudioStream(event, currentPlayerId)) {
      stopMicrophone();
    }
  }, [currentPlayerId, stopMicrophone]);
  useGameSessionRealtime(activeSessionId ?? 0, handleRoundEvent);

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

  function toggleDeafen() {
    voiceMesh.toggleDeafen();
  }

  if (!activeGroup) return null;

  return <aside className="flex w-[100px] shrink-0 flex-col items-center border-l-[3px] border-sidebar-border bg-sidebar/95 px-2 py-[22px] backdrop-blur"><div className="flex flex-col items-center gap-6">{voiceMembers.map((member, index) => <div key={member.id} className="flex flex-col items-center gap-2"><div className={`flex size-[52px] items-center justify-center rounded-full font-display text-base text-primary-foreground ${MEMBER_COLORS[index % MEMBER_COLORS.length]}`}>{member.displayName?.charAt(0).toUpperCase() ?? "?"}</div><span className="max-w-[84px] truncate text-[10px] text-sidebar-foreground">{member.displayName ?? "Player"}</span></div>)}</div><div className="flex-1" />{microphoneError || voiceMesh.microphoneError ? <p className="mb-2 text-center text-[9px] text-destructive">Microphone permission is needed.</p> : null}{voiceMesh.tabAudioError ? <p className="mb-2 text-center text-[9px] text-destructive">Couldn&apos;t share tab audio.</p> : null}<div className="flex flex-col items-center gap-2.5">{isInVoice ? <><Button type="button" variant="ghost" size="icon-lg" title="Mute" aria-label="Mute" onClick={toggleMute} className="rounded-xl bg-card text-card-foreground">{voiceMesh.isMuted ? <MicOff className="size-[19px]" /> : <Mic className="size-[19px]" />}</Button><Button type="button" variant="ghost" size="icon-lg" title="Deafen" aria-label="Deafen" onClick={toggleDeafen} className={`rounded-xl ${voiceMesh.isDeafened ? "bg-primary text-primary-foreground" : "bg-card text-card-foreground"}`}><Headphones className="size-[19px]" /></Button><div className="my-1 h-0.5 w-8 bg-sidebar-border" /><Button type="button" variant="destructive" size="icon-lg" title="Leave voice" aria-label="Leave voice" onClick={leaveCall} disabled={leaveVoice.isPending} className="rounded-xl">{leaveVoice.isPending ? <Loader2 className="size-4 animate-spin" /> : <PhoneOff className="size-[19px]" />}</Button></> : <Button type="button" variant="outline" size="icon-lg" title="Join call" aria-label="Join call" onClick={joinCall} disabled={joinVoice.isPending} className="rounded-full border-dashed border-primary text-primary">{joinVoice.isPending ? <Loader2 className="size-4 animate-spin" /> : <Phone className="size-[19px]" />}</Button>}</div></aside>;
}
