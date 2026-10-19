"use client";

import { useEffect, useState } from "react";
import { HeadphoneOff, MicOff } from "lucide-react";
import Image from "next/image";
import type { MemberDTO } from "@/hooks/models/memberDTO";
import type { VoiceStatus } from "@/lib/voice-status";

const MEMBER_EXIT_MILLISECONDS = 220;
const AVATAR_SIZE_PIXELS = 46;
const MEMBER_COLORS = ["bg-primary text-primary-foreground", "bg-accent text-accent-foreground", "bg-warning text-warning-foreground", "bg-secondary text-secondary-foreground"];
const UNKNOWN_STATUS: VoiceStatus = { isMuted: false, isDeafened: false, isSpeaking: false };

export function VoiceMemberList({ members, currentUserId, localStatus = UNKNOWN_STATUS, memberStatuses = {} }: { members: MemberDTO[]; currentUserId?: number; localStatus?: VoiceStatus; memberStatuses?: Record<number, VoiceStatus> }) {
  const [retainedMembers, setRetainedMembers] = useState(members);
  const [previousMemberIds, setPreviousMemberIds] = useState(members.map((member) => member.userId).join(","));
  const memberIds = members.map((member) => member.userId).join(",");
  if (previousMemberIds !== memberIds) {
    setPreviousMemberIds(memberIds);
    setRetainedMembers((previous) => [...members, ...previous.filter((member) => !members.some((current) => current.userId === member.userId))]);
  }
  const departingMembers = retainedMembers.filter((member) => !members.some((current) => current.userId === member.userId));
  useEffect(() => {
    if (!departingMembers.length) return;
    const timeout = window.setTimeout(() => setRetainedMembers((previous) => previous.filter((member) => members.some((current) => current.userId === member.userId))), MEMBER_EXIT_MILLISECONDS);
    return () => window.clearTimeout(timeout);
  }, [memberIds, departingMembers.length, members]);
  const displayedMembers = retainedMembers.map((retained) => members.find((member) => member.userId === retained.userId) ?? retained);

  return <div className="flex min-h-0 flex-col items-center overflow-y-auto px-2 pt-2">
    {displayedMembers.map((member) => {
      const name = member.displayName ?? "Player";
      const isDeparting = departingMembers.some((departing) => departing.userId === member.userId);
      const status = member.userId === currentUserId ? localStatus : memberStatuses[member.userId ?? 0];
      const statusLabel = status ? [status.isMuted ? "muted" : "microphone on", status.isDeafened ? "deafened" : "listening", status.isSpeaking ? "speaking" : ""].filter(Boolean).join(", ") : "connecting";
      const colorIndex = (member.userId ?? 0) % MEMBER_COLORS.length;
      return <div key={member.userId} aria-label={`${name}, ${statusLabel}`} aria-hidden={isDeparting || undefined} className={`voice-member flex h-[86px] shrink-0 flex-col items-center gap-1.5 ${isDeparting ? "voice-member-leaving" : ""}`}>
        <div className={`relative flex size-[46px] shrink-0 items-center justify-center rounded-full font-display text-base transition-shadow ${MEMBER_COLORS.at(colorIndex)} ${status?.isSpeaking ? "ring-[3px] ring-green ring-offset-2 ring-offset-sidebar" : ""}`}>
          {name.charAt(0).toUpperCase()}
          {member.avatarUrl ? <Image key={member.avatarUrl} src={member.avatarUrl} alt="" width={AVATAR_SIZE_PIXELS} height={AVATAR_SIZE_PIXELS} unoptimized className="absolute inset-0 size-full rounded-full object-cover" onError={(event) => { event.currentTarget.style.display = "none"; }} /> : null}
          {status?.isMuted ? <span title="Muted" className="absolute -bottom-1 -left-1 rounded-full bg-sidebar p-0.5 text-destructive"><MicOff className="size-3.5" /></span> : null}
          {status?.isDeafened ? <span title="Deafened" className="absolute -bottom-1 -right-1 rounded-full bg-sidebar p-0.5 text-warning"><HeadphoneOff className="size-3.5" /></span> : null}
        </div>
        <span className="max-w-[64px] truncate text-[10px] text-sidebar-foreground">{name}</span>
      </div>;
    })}
  </div>;
}
