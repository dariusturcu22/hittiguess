"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Headphones, Loader2, Mic, MicOff, Phone, PhoneOff, Settings } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { usePathname } from "next/navigation";

import { getGetActiveMembershipQueryKey, getGetGroupQueryKey, useGetActiveMembership, useJoinVoice, useLeaveVoice } from "@/hooks/generated/group-management/group-management";
import { useGetActiveSessionForGroup, useGetSession } from "@/hooks/generated/game-session/game-session";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import { useGameSessionRealtime } from "@/hooks/use-game-session-realtime";
import { shouldSilenceDjForActivePlayer, useVoiceMesh } from "@/hooks/use-voice-mesh";
import { useAudioDevices } from "@/hooks/use-audio-devices";
import { DJ_AUDIO_SHARE_EVENT } from "@/hooks/use-local-audio-stream";
import { Button } from "@/components/shadcn/button";
import { VoiceSettingsPopup } from "@/components/voice-settings-popup";

const LOBBY_PATH_PATTERN = /^\/groups\/\d+/;
// Matches the left sidebar's width in every state, in a call or not.
const RAIL_CLASSES = "flex w-[76px] shrink-0 flex-col items-center border-l-[3px] border-sidebar-border bg-sidebar py-5";

const MEMBER_COLORS = [
  "bg-primary text-primary-foreground",
  "bg-accent text-accent-foreground",
  "bg-warning text-warning-foreground",
  "bg-secondary text-secondary-foreground",
  "bg-primary text-primary-foreground",
  "bg-accent text-accent-foreground",
];

export function AppVoiceSidebar() {
  const queryClient = useQueryClient();
  const activeMembershipQuery = useGetActiveMembership({ query: { retry: false } });
  const currentUserQuery = useGetCurrentUser();
  const joinVoice = useJoinVoice();
  const leaveVoice = useLeaveVoice();
  const [microphoneError, setMicrophoneError] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  // Set from the join click until the server confirms the call. The signalling socket
  // connects during this window, before the join is announced, so the offers other
  // members send the moment they hear about it have somewhere to land.
  const [isJoiningCall, setIsJoiningCall] = useState(false);
  const isJoinRequestSentReference = useRef(false);
  const pathname = usePathname();
  const { microphoneDeviceId, speakerDeviceId } = useAudioDevices();
  const activeGroup = activeMembershipQuery.data;
  const groupId = activeGroup?.id;
  const activeSessionQuery = useGetActiveSessionForGroup(groupId ?? 0, { query: { enabled: groupId !== undefined, retry: false } });
  const activeSessionId = activeSessionQuery.data?.id;
  const sessionQuery = useGetSession(activeSessionId ?? 0, { query: { enabled: activeSessionId !== undefined, retry: false } });
  const currentMember = activeGroup?.members?.find((member) => member.userId === currentUserQuery.data?.id);
  const isInVoice = Boolean(currentMember?.isInVoice);
  const isOnLobbyPage = LOBBY_PATH_PATTERN.test(pathname);
  const voiceMembers = (activeGroup?.members ?? []).filter((member) => member.isInVoice);
  const refreshVoicePresence = useCallback(() => {
    if (groupId) void queryClient.invalidateQueries({ queryKey: getGetGroupQueryKey(groupId) });
    void queryClient.invalidateQueries({ queryKey: getGetActiveMembershipQueryKey() });
  }, [groupId, queryClient]);
  const voiceMesh = useVoiceMesh(groupId ?? 0, currentUserQuery.data?.id, voiceMembers, isInVoice || isJoiningCall, {
    microphoneDeviceId,
    speakerDeviceId,
    onPresenceChange: refreshVoicePresence,
  });
  const { setSilencedUserIds, startTabAudio, stopTabAudio } = voiceMesh;
  const microphoneMessage = voiceMesh.microphoneErrorMessage
    ?? ((microphoneError || voiceMesh.microphoneError) ? "Microphone permission is needed." : null);

  // The DJ's "Share YouTube audio" click reaches here as a window event, since the
  // session view and this sidebar share no props. The capture starts first, inside the
  // click's user activation, and joins the voice room for a DJ who wasn't in it yet.
  useEffect(() => {
    function shareDjTabAudio() {
      if (!groupId) return;
      void startTabAudio().then((isShared) => {
        if (isShared && !isInVoice) setIsJoiningCall(true);
      });
    }
    window.addEventListener(DJ_AUDIO_SHARE_EVENT, shareDjTabAudio);
    return () => window.removeEventListener(DJ_AUDIO_SHARE_EVENT, shareDjTabAudio);
  });

  const session = sessionQuery.data;
  const currentPlayerId = session?.players?.find((player) => player.userId === currentUserQuery.data?.id)?.id;
  const djUserId = session?.players?.find((player) => player.id === session.currentRound?.djPlayerId)?.userId;
  const isSilencingDj = shouldSilenceDjForActivePlayer(session?.currentRound, currentPlayerId);
  const isCurrentDj = currentPlayerId !== undefined && currentPlayerId === session?.currentRound?.djPlayerId;
  useEffect(() => {
    setSilencedUserIds(isSilencingDj && djUserId !== undefined ? [djUserId] : []);
  }, [djUserId, isSilencingDj, setSilencedUserIds]);
  useEffect(() => {
    if (!isCurrentDj) stopTabAudio();
  }, [isCurrentDj, stopTabAudio]);
  useGameSessionRealtime(activeSessionId ?? 0);

  const { isSignalConnected } = voiceMesh;
  const { mutate: announceJoin } = joinVoice;
  useEffect(() => {
    if (!isJoiningCall || !isSignalConnected || !groupId || isJoinRequestSentReference.current) return;
    isJoinRequestSentReference.current = true;
    announceJoin({ groupId }, {
      onSuccess: refreshVoicePresence,
      onError: () => setIsJoiningCall(false),
      onSettled: () => {
        isJoinRequestSentReference.current = false;
      },
    });
  }, [announceJoin, groupId, isJoiningCall, isSignalConnected, refreshVoicePresence]);
  // Once the refreshed membership shows this player in the call, the join is done.
  if (isInVoice && isJoiningCall) setIsJoiningCall(false);

  async function joinCall() {
    if (!groupId) return;
    try {
      await voiceMesh.startMicrophone();
      setMicrophoneError(false);
      setIsJoiningCall(true);
    } catch {
      setMicrophoneError(true);
    }
  }

  function leaveCall() {
    if (!groupId) return;
    voiceMesh.stopVoice();
    leaveVoice.mutate({ groupId }, { onSuccess: refreshVoicePresence });
  }

  function toggleMute() {
    voiceMesh.toggleMute();
  }

  function toggleDeafen() {
    voiceMesh.toggleDeafen();
  }

  if (!activeGroup) return null;

  // Outside a call the sidebar exists only on the group lobby page, with the join action
  // at the top. In a call it stays everywhere until the call ends. There is no collapse
  // control.
  if (!isInVoice && !isOnLobbyPage) return null;

  if (!isInVoice) return <aside aria-label="Voice sidebar" className={RAIL_CLASSES}><Button type="button" variant="outline" size="icon-lg" title="Join call" aria-label="Join call" onClick={joinCall} disabled={isJoiningCall} className="rounded-full border-dashed border-primary text-primary">{isJoiningCall ? <Loader2 className="size-4 animate-spin" /> : <Phone className="size-[19px]" />}</Button>{microphoneMessage ? <p className="mt-2 text-center text-[9px] text-destructive">{microphoneMessage}</p> : null}{voiceMesh.tabAudioError ? <p className="mt-2 text-center text-[9px] text-destructive">{voiceMesh.tabAudioErrorMessage}</p> : null}</aside>;

  return <aside aria-label="Voice sidebar" className={`relative ${RAIL_CLASSES}`}><div className="flex flex-col items-center gap-5">{voiceMembers.map((member, index) => <div key={member.id} className="flex flex-col items-center gap-1.5"><div className={`avatar-initial flex size-[46px] items-center justify-center rounded-full font-display text-base ${MEMBER_COLORS[index % MEMBER_COLORS.length]}`}>{member.displayName?.charAt(0).toUpperCase() ?? "?"}</div><span className="max-w-[64px] truncate text-[10px] text-sidebar-foreground">{member.displayName ?? "Player"}</span></div>)}</div><div className="flex-1" />{microphoneMessage ? <p className="mb-2 text-center text-[9px] text-destructive">{microphoneMessage}</p> : null}{voiceMesh.tabAudioError ? <p className="mb-2 text-center text-[9px] text-destructive">{voiceMesh.tabAudioErrorMessage}</p> : null}<div className="flex flex-col items-center gap-2.5">{isInVoice ? <><Button type="button" variant="ghost" size="icon-lg" title="Mute" aria-label="Mute" aria-pressed={voiceMesh.isMuted} onClick={toggleMute} className="rounded-xl bg-card text-card-foreground">{voiceMesh.isMuted ? <MicOff className="size-[19px]" /> : <Mic className="size-[19px]" />}</Button><Button type="button" variant="ghost" size="icon-lg" title="Deafen" aria-label="Deafen" aria-pressed={voiceMesh.isDeafened} onClick={toggleDeafen} className={`rounded-xl ${voiceMesh.isDeafened ? "bg-primary text-primary-foreground" : "bg-card text-card-foreground"}`}><Headphones className="size-[19px]" /></Button><Button type="button" variant="ghost" size="icon-lg" title="Voice settings" aria-label="Voice settings" onClick={() => setIsSettingsOpen((currentValue) => !currentValue)} className={`rounded-xl ${isSettingsOpen ? "bg-primary text-primary-foreground" : "bg-card text-card-foreground"}`}><Settings className="size-[19px]" /></Button><div className="my-1 h-0.5 w-8 bg-sidebar-border" /><Button type="button" variant="destructive" size="icon-lg" title="Leave voice" aria-label="Leave voice" onClick={leaveCall} disabled={leaveVoice.isPending} className="rounded-xl">{leaveVoice.isPending ? <Loader2 className="size-4 animate-spin" /> : <PhoneOff className="size-[19px]" />}</Button></> : <Button type="button" variant="outline" size="icon-lg" title="Join call" aria-label="Join call" onClick={joinCall} disabled={isJoiningCall} className="rounded-full border-dashed border-primary text-primary">{isJoiningCall ? <Loader2 className="size-4 animate-spin" /> : <Phone className="size-[19px]" />}</Button>}</div>{isSettingsOpen ? <VoiceSettingsPopup onClose={() => setIsSettingsOpen(false)} /> : null}</aside>;
}
