"use client";

import { use, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Clipboard,
  Crown,
  Loader2,
  LogOut,
  MessageCircle,
  Play,
  Settings,
} from "lucide-react";

import {
  getGetGroupQueryKey,
  useGetGroup,
  useLeaveGroup,
  useStartGameSession,
} from "@/hooks/generated/group-management/group-management";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import type { MemberDTO } from "@/hooks/models/memberDTO";
import { useQueryClient } from "@tanstack/react-query";

const MEMBER_COLORS = [
  "bg-primary text-primary-foreground",
  "bg-blue text-blue-foreground",
  "bg-warning text-warning-foreground",
  "bg-pink text-pink-foreground",
  "bg-green text-green-foreground",
  "bg-accent text-accent-foreground",
];

const ORBIT_POSITIONS = [
  "left-[13%] top-[18%] -rotate-6",
  "right-[13%] top-[12%] rotate-6",
  "bottom-[9%] left-[19%] rotate-6",
  "right-[9%] bottom-[12%] -rotate-6",
  "left-[4%] bottom-[35%] -rotate-3",
  "right-[3%] top-[39%] rotate-3",
  "left-[35%] top-[4%] -rotate-2",
  "right-[35%] bottom-[3%] rotate-2",
];

const LOADING_MEMBER_COUNT = 4;

interface PageProps {
  params: Promise<{ groupId: string }>;
}

function memberInitial(member: MemberDTO): string {
  return member.displayName?.trim().charAt(0).toUpperCase() || "?";
}

function LobbyMember({
  member,
  index,
  isCurrentUser,
}: {
  member: MemberDTO;
  index: number;
  isCurrentUser: boolean;
}) {
  const colorClass = MEMBER_COLORS[index % MEMBER_COLORS.length];
  const orbitPosition = ORBIT_POSITIONS[index % ORBIT_POSITIONS.length];

  return (
    <div className={`absolute ${orbitPosition} flex flex-col items-center`}>
      <div className="relative">
        <div
          className={`flex size-[76px] items-center justify-center rounded-full font-display text-2xl shadow-[0_0_0_3px_var(--background),0_0_0_8px_var(--green)] sm:size-[108px] sm:text-[34px] ${colorClass} ${
            member.isConnected ? "" : "opacity-50 grayscale"
          }`}
        >
          {memberInitial(member)}
        </div>
        {member.isAdmin ? (
          <Crown className="absolute -right-2 -top-2 size-6 fill-warning text-warning drop-shadow-sm sm:size-7" />
        ) : null}
      </div>
      <span className="mt-2 max-w-[112px] truncate font-semibold text-sm text-foreground sm:mt-3 sm:text-[15px]">
        {member.displayName || "Player"}
        {isCurrentUser ? " (you)" : ""}
      </span>
      {!member.isConnected ? (
        <span className="mt-0.5 text-[10px] text-muted-foreground">Away</span>
      ) : null}
    </div>
  );
}

function LobbyLoadingState() {
  return (
    <div className="relative flex min-h-[480px] flex-1 items-center justify-center overflow-hidden">
      {Array.from({ length: LOADING_MEMBER_COUNT }, (_, index) => (
        <div
          key={index}
          className={`absolute ${ORBIT_POSITIONS[index]} size-24 animate-pulse rounded-full bg-card sm:size-[108px]`}
        />
      ))}
      <Loader2 className="size-8 animate-spin text-primary" aria-label="Loading group" />
    </div>
  );
}

export default function GroupLobbyPage({ params }: PageProps) {
  const { groupId: groupIdParam } = use(params);
  const groupId = Number(groupIdParam);
  const router = useRouter();
  const queryClient = useQueryClient();
  const [copyFeedback, setCopyFeedback] = useState("");
  const groupQuery = useGetGroup(groupId, { query: { retry: false } });
  const { data: currentUser } = useGetCurrentUser();
  const startSession = useStartGameSession();
  const leaveGroup = useLeaveGroup();

  const members = useMemo(() => groupQuery.data?.members ?? [], [groupQuery.data?.members]);
  const currentMember = members.find((member) => member.id === currentUser?.id);
  const isCurrentUserAdmin = Boolean(currentMember?.isAdmin);
  const featuredPlaylist = groupQuery.data?.playlists?.[0];

  function refreshGroup() {
    queryClient.invalidateQueries({ queryKey: getGetGroupQueryKey(groupId) });
  }

  async function copyInviteLink() {
    const inviteCode = groupQuery.data?.inviteCode;
    if (!inviteCode) {
      return;
    }

    await navigator.clipboard.writeText(`${window.location.origin}/groups/join/${inviteCode}`);
    setCopyFeedback("Invite link copied");
  }

  function handleStartGame() {
    startSession.mutate({ groupId }, { onSuccess: refreshGroup });
  }

  function handleLeaveLobby() {
    leaveGroup.mutate(
      { groupId },
      { onSuccess: () => router.push("/playlists") },
    );
  }

  if (!Number.isInteger(groupId) || groupId <= 0) {
    return <main className="p-10 text-destructive">This group link is invalid.</main>;
  }

  if (groupQuery.isLoading) {
    return (
      <main className="flex h-full min-h-[720px] flex-col px-6 py-8 sm:px-14 sm:py-9">
        <div className="h-10 w-52 animate-pulse rounded bg-card" />
        <LobbyLoadingState />
      </main>
    );
  }

  if (groupQuery.isError || !groupQuery.data) {
    return (
      <main className="flex h-full min-h-[720px] items-center justify-center p-8">
        <section className="max-w-md rounded-[20px] border-2 border-destructive bg-card p-6 text-center shadow-md">
          <h1 className="font-display text-2xl text-card-foreground">Group unavailable</h1>
          <p className="mt-3 text-sm text-muted-foreground">
            This group may have ended, or this account no longer has access to it.
          </p>
          <button
            type="button"
            onClick={() => router.push("/playlists")}
            className="mt-5 rounded-full bg-primary px-5 py-3 font-semibold text-primary-foreground"
          >
            Back to playlists
          </button>
        </section>
      </main>
    );
  }

  return (
    <main className="flex h-full min-h-[720px] flex-col px-6 py-8 sm:px-14 sm:py-9">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <h1 className="font-display text-[26px] text-foreground drop-shadow-sm sm:text-[32px]">
          Group Lobby
        </h1>
        <div className="inline-flex w-fit items-center gap-2.5 rounded-full border-2 border-border bg-card py-2 pl-2 pr-4">
          <span
            className="size-[26px] rounded-[8px]"
            style={{ backgroundColor: featuredPlaylist ? `#${featuredPlaylist.color}` : "var(--primary)" }}
          />
          <span className="font-semibold text-[13px] text-card-foreground">
            {featuredPlaylist?.name ?? "No playlist selected"}
          </span>
          {featuredPlaylist ? (
            <span className="text-xs text-muted-foreground">{featuredPlaylist.songCount} songs</span>
          ) : null}
        </div>
      </header>

      <section className="relative flex min-h-[480px] flex-1 items-center justify-center overflow-hidden py-10">
        <div className="z-10 text-center">
          <p className="mb-3 font-semibold text-[11px] uppercase tracking-[0.25em] text-muted-foreground">
            Enter code to join
          </p>
          <p className="pl-3 font-display text-5xl tracking-[0.3em] text-foreground drop-shadow-sm sm:text-[76px]">
            {groupQuery.data.joinCode ?? "----"}
          </p>
          <button
            type="button"
            onClick={copyInviteLink}
            className="mt-5 inline-flex items-center gap-2 text-[13px] font-semibold text-muted-foreground transition-colors hover:text-foreground"
          >
            <Clipboard className="size-4" />
            {copyFeedback || "Copy invite link"}
          </button>
        </div>

        {members.map((member, index) => (
          <LobbyMember
            key={member.id ?? `${member.displayName}-${index}`}
            member={member}
            index={index}
            isCurrentUser={member.id === currentUser?.id}
          />
        ))}
      </section>

      <footer className="flex flex-wrap items-center gap-3">
        {isCurrentUserAdmin ? (
          <button
            type="button"
            onClick={handleStartGame}
            disabled={startSession.isPending || groupQuery.data.status !== "OPEN"}
            className="inline-flex items-center gap-2 rounded-full bg-accent px-5 py-3 font-display text-[13px] text-accent-foreground shadow-md disabled:cursor-not-allowed disabled:opacity-60"
          >
            {startSession.isPending ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4 fill-current" />}
            Start game
          </button>
        ) : null}
        <button
          type="button"
          disabled
          title="Chat is added in the next Batch E step"
          className="inline-flex items-center gap-2 rounded-full border-2 border-border bg-card px-5 py-3 text-[13px] font-semibold text-card-foreground disabled:opacity-60"
        >
          <MessageCircle className="size-4" />
          Chat
        </button>
        {isCurrentUserAdmin ? (
          <button
            type="button"
            disabled
            title="Group settings are added in the next Batch E step"
            className="inline-flex items-center gap-2 rounded-full border-2 border-border bg-card px-5 py-3 text-[13px] font-semibold text-card-foreground disabled:opacity-60"
          >
            <Settings className="size-4" />
            Settings
          </button>
        ) : null}
        <div className="flex-1" />
        <button
          type="button"
          onClick={handleLeaveLobby}
          disabled={leaveGroup.isPending}
          className="inline-flex items-center gap-2 rounded-full border-2 border-destructive bg-transparent px-5 py-3 text-[13px] font-semibold text-destructive disabled:opacity-60"
        >
          {leaveGroup.isPending ? <Loader2 className="size-4 animate-spin" /> : <LogOut className="size-4" />}
          Leave lobby
        </button>
      </footer>
    </main>
  );
}
