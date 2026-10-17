"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Headphones, Loader2, Mic, MicOff, Phone, PhoneOff, Settings } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";

import { getGetActiveMembershipQueryKey, getGetGroupQueryKey, useGetActiveMembership, useJoinVoice, useLeaveVoice, useReportAudioCapability } from "@/hooks/generated/group-management/group-management";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import { useVoiceMesh } from "@/hooks/use-voice-mesh";
import { useAudioDevices } from "@/hooks/use-audio-devices";
import { Button } from "@/components/shadcn/button";
import { canShareTabAudio } from "@/lib/youtube-link-out";
import { VoiceSettingsPopup } from "@/components/voice-settings-popup";
import { VoiceMemberList } from "@/components/voice-member-list";

// Matches the left sidebar's width in every state, in a call or not.
const RAIL_CLASSES = "flex h-full min-h-0 w-[76px] shrink-0 flex-col items-center border-l-[3px] border-sidebar-border bg-sidebar py-5 short:py-4 very-short:py-3";


export function AppVoiceSidebar() {
  const queryClient = useQueryClient();
  const activeMembershipQuery = useGetActiveMembership({ query: { retry: false } });
  const currentUserQuery = useGetCurrentUser();
  const joinVoice = useJoinVoice();
  const leaveVoice = useLeaveVoice();
  const { mutate: reportAudioCapability } = useReportAudioCapability();
  const [microphoneError, setMicrophoneError] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  // Set from the join click until the server confirms the call. The signalling socket
  // connects during this window, before the join is announced, so the offers other
  // members send the moment they hear about it have somewhere to land.
  const [isJoiningCall, setIsJoiningCall] = useState(false);
  const isJoinRequestSentReference = useRef(false);
  const { microphoneDeviceId, speakerDeviceId, microphoneVolume, speakerVolume } = useAudioDevices();
  const activeGroup = activeMembershipQuery.data;
  const groupId = activeGroup?.id;
  const currentMember = activeGroup?.members?.find((member) => member.userId === currentUserQuery.data?.id);
  const isInVoice = Boolean(currentMember?.isInVoice);
  // Tells the server once per group whether this browser can capture audio, so the DJ
  // rotation skips a player who could not share the song (Firefox, Safari, phones).
  const reportedGroupReference = useRef<number | undefined>(undefined);
  useEffect(() => {
    if (groupId === undefined || !currentUserQuery.data || reportedGroupReference.current === groupId) return;
    reportedGroupReference.current = groupId;
    reportAudioCapability({ groupId, data: { canShareAudio: canShareTabAudio(navigator.userAgent) } });
  }, [currentUserQuery.data, groupId, reportAudioCapability]);
  const voiceMembers = (activeGroup?.members ?? []).filter((member) => member.isInVoice);
  const refreshVoicePresence = useCallback(() => {
    if (groupId) void queryClient.invalidateQueries({ queryKey: getGetGroupQueryKey(groupId) });
    void queryClient.invalidateQueries({ queryKey: getGetActiveMembershipQueryKey() });
  }, [groupId, queryClient]);
  const voiceMesh = useVoiceMesh(groupId ?? 0, currentUserQuery.data?.id, voiceMembers, isInVoice || isJoiningCall, {
    microphoneDeviceId,
    speakerDeviceId,
    microphoneVolume,
    speakerVolume,
    onPresenceChange: refreshVoicePresence,
  });
  const microphoneMessage = voiceMesh.microphoneErrorMessage
    ?? ((microphoneError || voiceMesh.microphoneError) ? "Microphone permission is needed." : null);

  // Leaving the group while in the call (Leave game, Leave lobby, being removed) ends the
  // call's audio too, so no microphone capture outlives the membership.
  const { stopVoice } = voiceMesh;
  const hasGroup = activeGroup !== undefined;
  useEffect(() => {
    if (!hasGroup && !activeMembershipQuery.isLoading) stopVoice();
  }, [activeMembershipQuery.isLoading, hasGroup, stopVoice]);

  const { isSignalConnected, isVoiceStarted } = voiceMesh;
  const { mutate: announceJoin } = joinVoice;
  const { mutate: announceLeave } = leaveVoice;
  // The server keeps listing a member as in the call after a reload or a closed tab, but
  // this browser has no microphone or peers then. That stale entry is cleared so the
  // roster never shows someone who is not actually in the call.
  const isStaleLeaveSentReference = useRef(false);
  useEffect(() => {
    if (!isInVoice) {
      isStaleLeaveSentReference.current = false;
      return;
    }
    if (isVoiceStarted || isJoiningCall || !groupId || !currentUserQuery.data || isStaleLeaveSentReference.current) return;
    isStaleLeaveSentReference.current = true;
    announceLeave({ groupId }, { onSuccess: refreshVoicePresence });
  }, [announceLeave, currentUserQuery.data, groupId, isInVoice, isJoiningCall, isVoiceStarted, refreshVoicePresence]);
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

  const memberList = <VoiceMemberList members={voiceMembers} currentUserId={currentUserQuery.data?.id} localStatus={voiceMesh.localStatus} memberStatuses={voiceMesh.memberStatuses} />;
  const joinButton = <Button type="button" variant="outline" size="icon-lg" title="Join call" aria-label="Join call" onClick={joinCall} disabled={isJoiningCall} className="rounded-full border-dashed border-primary text-primary">{isJoiningCall ? <Loader2 className="size-4 animate-spin" /> : <Phone className="size-[19px]" />}</Button>;

  // Outside a call the rail still lists who is in one, so a player can see whether anyone
  // is around before joining. It is hidden only when the user is in no group.
  if (!isInVoice) return <aside aria-label="Voice sidebar" className={RAIL_CLASSES}>{memberList}<div className="flex-1" />{microphoneMessage ? <p className="mb-2 text-center text-[9px] text-destructive">{microphoneMessage}</p> : null}<div className="flex shrink-0 flex-col items-center gap-2.5">{joinButton}</div></aside>;

  return <aside aria-label="Voice sidebar" className={`relative ${RAIL_CLASSES}`}>{memberList}<div className="flex-1" />{microphoneMessage ? <p className="mb-2 text-center text-[9px] text-destructive">{microphoneMessage}</p> : null}<div className="flex shrink-0 flex-col items-center gap-2.5">{isInVoice ? <><Button type="button" variant="ghost" size="icon-lg" title="Mute" aria-label="Mute" aria-pressed={voiceMesh.isMuted} onClick={toggleMute} className="rounded-xl bg-card text-card-foreground">{voiceMesh.isMuted ? <MicOff className="size-[19px]" /> : <Mic className="size-[19px]" />}</Button><Button type="button" variant="ghost" size="icon-lg" title="Deafen" aria-label="Deafen" aria-pressed={voiceMesh.isDeafened} onClick={toggleDeafen} className={`rounded-xl ${voiceMesh.isDeafened ? "bg-primary text-primary-foreground" : "bg-card text-card-foreground"}`}><Headphones className="size-[19px]" /></Button><Button type="button" variant="ghost" size="icon-lg" title="Voice settings" aria-label="Voice settings" onClick={() => setIsSettingsOpen((currentValue) => !currentValue)} className={`rounded-xl ${isSettingsOpen ? "bg-primary text-primary-foreground" : "bg-card text-card-foreground"}`}><Settings className="size-[19px]" /></Button><div className="my-1 h-0.5 w-8 bg-sidebar-border" /><Button type="button" variant="destructive" size="icon-lg" title="Leave voice" aria-label="Leave voice" onClick={leaveCall} disabled={leaveVoice.isPending} className="rounded-xl">{leaveVoice.isPending ? <Loader2 className="size-4 animate-spin" /> : <PhoneOff className="size-[19px]" />}</Button></> : joinButton}</div>{isSettingsOpen ? <VoiceSettingsPopup onClose={() => setIsSettingsOpen(false)} /> : null}</aside>;
}
