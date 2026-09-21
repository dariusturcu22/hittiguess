"use client";

import { use, useCallback, useEffect, useMemo, useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  Check,
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
  getGetActiveMembershipQueryKey,
  useGetGroup,
  useLeaveGroup,
  useStartGameSession,
  useUpdateGroupSettings,
} from "@/hooks/generated/group-management/group-management";
import { useGetCurrentUser, useGetUserPlaylists } from "@/hooks/generated/user-management/user-management";
import { useGetActiveSessionForGroup } from "@/hooks/generated/game-session/game-session";
import {
  useGenerateDifficultySet,
  useStartCustomSession,
  useStartSessionWithSongs,
  type DifficultyTier,
  type GeneratedSongPreviewDTO,
} from "@/hooks/use-difficulty-session-start";
import type { MemberDTO } from "@/hooks/models/memberDTO";
import { useQueryClient } from "@tanstack/react-query";
import { GroupChatOverlay } from "@/components/group-chat-overlay";
import { useGroupRealtime } from "@/hooks/use-group-realtime";

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
const MINIMUM_WIN_CONDITION = 1;
const MINIMUM_TARGET_CARD_COUNT = 1;
const DIFFICULTY_TIERS: DifficultyTier[] = ["EASY", "MEDIUM", "HARD"];

function mutationErrorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null && "response" in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    if (response?.data?.message) {
      return response.data.message;
    }
  }
  return "Something went wrong. Try again.";
}

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
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isChatOpen, setIsChatOpen] = useState(false);
  const [isStartOptionsOpen, setIsStartOptionsOpen] = useState(false);
  const [startMode, setStartMode] = useState<"difficulty" | "custom">("difficulty");
  const [selectedTier, setSelectedTier] = useState<DifficultyTier>("MEDIUM");
  const [targetCardCount, setTargetCardCount] = useState(MINIMUM_WIN_CONDITION);
  const [reviewedSongs, setReviewedSongs] = useState<GeneratedSongPreviewDTO[] | null>(null);
  const [selectedCustomPlaylistId, setSelectedCustomPlaylistId] = useState<number | undefined>(undefined);
  const [playlistLink, setPlaylistLink] = useState("");
  const [startError, setStartError] = useState("");
  const [selectedDjMode, setSelectedDjMode] = useState<"FIXED" | "ROTATING">("ROTATING");
  const [selectedPlaylistId, setSelectedPlaylistId] = useState<number | undefined>(undefined);
  const [winCondition, setWinCondition] = useState(MINIMUM_WIN_CONDITION);
  const groupQuery = useGetGroup(groupId, { query: { retry: false } });
  const groupRealtime = useGroupRealtime(groupId);
  const { data: currentUser } = useGetCurrentUser();
  const playlistsQuery = useGetUserPlaylists({ query: { retry: false } });
  const startSession = useStartGameSession();
  const generateSet = useGenerateDifficultySet();
  const startWithSongs = useStartSessionWithSongs();
  const startCustom = useStartCustomSession();
  const leaveGroup = useLeaveGroup();
  const updateSettings = useUpdateGroupSettings();
  const activeSessionQuery = useGetActiveSessionForGroup(groupId, {
    query: { enabled: groupQuery.data?.status === "LOCKED", retry: false },
  });

  const members = useMemo(() => groupQuery.data?.members ?? [], [groupQuery.data?.members]);
  const currentMember = members.find((member) => member.userId === currentUser?.id);
  const isCurrentUserAdmin = Boolean(currentMember?.isAdmin);
  const isCurrentUserLoading = currentUser === undefined;
  const featuredPlaylist = groupQuery.data?.playlists?.[0];

  useEffect(() => {
    if (activeSessionQuery.data?.id) {
      router.replace(`/sessions/${activeSessionQuery.data.id}`);
    }
  }, [activeSessionQuery.data?.id, router]);

  function refreshGroup() {
    queryClient.invalidateQueries({ queryKey: getGetGroupQueryKey(groupId) });
  }

  function refreshActiveMembership() {
    queryClient.invalidateQueries({ queryKey: getGetActiveMembershipQueryKey() });
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
    startSession.mutate({ groupId }, { onSuccess: () => { refreshGroup(); refreshActiveMembership(); } });
  }

  function openStartOptions() {
    setStartMode("difficulty");
    setReviewedSongs(null);
    setStartError("");
    setPlaylistLink("");
    setSelectedCustomPlaylistId(undefined);
    setTargetCardCount(
      Math.max(
        MINIMUM_TARGET_CARD_COUNT,
        (groupQuery.data?.winConditionCardCount ?? MINIMUM_WIN_CONDITION) + members.length,
      ),
    );
    setIsStartOptionsOpen(true);
  }

  function closeStartOptions() {
    setIsStartOptionsOpen(false);
    setReviewedSongs(null);
    setStartError("");
  }

  function PlaylistPreselectCapture({ onCapture }: { onCapture: (playlistId: number | null) => void }) {
    const searchParams = useSearchParams();

    useEffect(() => {
      const rawPlaylistId = searchParams.get("playlist");
      if (rawPlaylistId === null) {
        onCapture(null);
        return;
      }
      const parsedPlaylistId = Number(rawPlaylistId);
      onCapture(Number.isInteger(parsedPlaylistId) && parsedPlaylistId > 0 ? parsedPlaylistId : null);
    }, [searchParams, onCapture]);

    return null;
  }

  const applyPlaylistPreselect = useCallback((playlistId: number | null) => {
    if (playlistId === null) {
      return;
    }
    setStartMode("custom");
    setReviewedSongs(null);
    setStartError("");
    setSelectedCustomPlaylistId(playlistId);
    setIsStartOptionsOpen(true);
  }, []);

  function handleModeStartSuccess() {
    refreshGroup();
    refreshActiveMembership();
    closeStartOptions();
  }

  function handleGenerateSet() {
    setStartError("");
    generateSet.mutate(
      { groupId, data: { tier: selectedTier, targetCardCount } },
      {
        onSuccess: (previews) => setReviewedSongs(previews),
        onError: (error) => setStartError(mutationErrorMessage(error)),
      },
    );
  }

  function handleConfirmGeneratedSet() {
    if (!reviewedSongs) {
      return;
    }
    setStartError("");
    startWithSongs.mutate(
      { groupId, data: { songIds: reviewedSongs.map((preview) => preview.id) } },
      {
        onSuccess: handleModeStartSuccess,
        onError: (error) => setStartError(mutationErrorMessage(error)),
      },
    );
  }

  function handleStartCustomPlaylist() {
    if (selectedCustomPlaylistId === undefined) {
      return;
    }
    setStartError("");
    startCustom.mutate(
      { groupId, data: { playlistId: selectedCustomPlaylistId } },
      {
        onSuccess: handleModeStartSuccess,
        onError: (error) => setStartError(mutationErrorMessage(error)),
      },
    );
  }

  function handleStartPlaylistLink() {
    if (!playlistLink.trim()) {
      return;
    }
    setStartError("");
    startCustom.mutate(
      { groupId, data: { playlistLink: playlistLink.trim() } },
      {
        onSuccess: handleModeStartSuccess,
        onError: (error) => setStartError(mutationErrorMessage(error)),
      },
    );
  }

  function handleLeaveLobby() {
    leaveGroup.mutate(
      { groupId },
      { onSuccess: () => { refreshActiveMembership(); router.push("/playlists"); } },
    );
  }

  function openSettings() {
    setSelectedDjMode(groupQuery.data?.djMode ?? "ROTATING");
    setSelectedPlaylistId(groupQuery.data?.playlists?.[0]?.id);
    setWinCondition(groupQuery.data?.winConditionCardCount ?? MINIMUM_WIN_CONDITION);
    setIsSettingsOpen(true);
  }

  function saveSettings() {
    const playlistIds = selectedPlaylistId === undefined ? [] : [selectedPlaylistId];

    updateSettings.mutate(
      {
        groupId,
        data: {
          playlistIds,
          djMode: selectedDjMode,
          winConditionCardCount: winCondition,
        },
      },
      { onSuccess: () => { refreshGroup(); setIsSettingsOpen(false); } },
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
      <Suspense fallback={null}>
        <PlaylistPreselectCapture onCapture={applyPlaylistPreselect} />
      </Suspense>
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
            isCurrentUser={member.userId === currentUser?.id}
          />
        ))}
        {isSettingsOpen ? (
          <section className="absolute bottom-7 left-0 z-20 w-full max-w-[400px] rounded-[18px] border-[3px] border-border bg-card/95 p-6 shadow-[6px_6px_0_rgba(0,0,0,0.35)] backdrop-blur sm:left-[228px] sm:p-7">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="font-display text-lg text-card-foreground">Settings</h2>
              <button type="button" onClick={() => setIsSettingsOpen(false)} className="text-muted-foreground hover:text-card-foreground">Close</button>
            </div>
            <div className="space-y-4">
              <div>
                <p className="mb-2 text-[13px] text-muted-foreground">Playlist</p>
                <div className="grid gap-2">
                  <button
                    type="button"
                    onClick={() => setSelectedPlaylistId(undefined)}
                    className={`flex items-center justify-between rounded-xl border-2 px-3 py-2 text-left text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${selectedPlaylistId === undefined ? "border-primary bg-primary/10 text-foreground" : "border-border bg-background text-muted-foreground hover:border-primary/60 hover:text-foreground"}`}
                  >
                    No playlist
                    {selectedPlaylistId === undefined ? <Check className="size-4 text-primary" /> : null}
                  </button>
                  {playlistsQuery.data?.map((playlist) => {
                    const isSelected = selectedPlaylistId === playlist.id;
                    return (
                      <button
                        key={playlist.id}
                        type="button"
                        onClick={() => setSelectedPlaylistId(playlist.id)}
                        className={`flex items-center justify-between rounded-xl border-2 px-3 py-2 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${isSelected ? "border-primary bg-primary/10" : "border-border bg-background hover:border-primary/60"}`}
                      >
                        <span>
                          <span className="block text-sm font-semibold text-foreground">{playlist.name}</span>
                          <span className="text-xs text-muted-foreground">{playlist.songCount} songs</span>
                        </span>
                        {isSelected ? <Check className="size-4 shrink-0 text-primary" /> : null}
                      </button>
                    );
                  })}
                </div>
              </div>
              <label className="flex items-center justify-between gap-3 text-[13px] text-muted-foreground">
                DJ mode
                <select value={selectedDjMode} onChange={(event) => setSelectedDjMode(event.target.value as "FIXED" | "ROTATING")} className="rounded-full border-2 border-border bg-background px-3 py-1.5 text-sm font-semibold text-foreground outline-none">
                  <option value="ROTATING">Rotating</option>
                  <option value="FIXED">Fixed</option>
                </select>
              </label>
              <label className="flex items-center justify-between gap-3 text-[13px] text-muted-foreground">
                Cards to win
                <input type="number" min={MINIMUM_WIN_CONDITION} value={winCondition} onChange={(event) => setWinCondition(Math.max(MINIMUM_WIN_CONDITION, Number(event.target.value)))} className="w-20 rounded-full border-2 border-border bg-background px-3 py-1.5 text-right text-sm font-semibold text-foreground outline-none" />
              </label>
            </div>
            <div className="mt-6 flex gap-2.5"><button type="button" onClick={saveSettings} disabled={updateSettings.isPending} className="rounded-full bg-primary px-4 py-2.5 font-display text-xs text-primary-foreground disabled:opacity-60">{updateSettings.isPending ? "Saving" : "Save changes"}</button><button type="button" onClick={() => setIsSettingsOpen(false)} className="rounded-full border-2 border-border px-4 py-2.5 text-xs font-semibold text-card-foreground">Close</button></div>
          </section>
        ) : null}
        {isStartOptionsOpen ? (
          <section aria-label="Custom start options" className="absolute bottom-7 left-0 z-20 w-full max-w-[440px] rounded-[18px] border-[3px] border-border bg-card/95 p-6 shadow-[6px_6px_0_rgba(0,0,0,0.35)] backdrop-blur sm:left-[228px] sm:p-7">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="font-display text-lg text-card-foreground">Start options</h2>
              <button type="button" onClick={closeStartOptions} className="text-muted-foreground hover:text-card-foreground">Close</button>
            </div>
            <div className="mb-4 flex gap-2">
              <button
                type="button"
                onClick={() => { setStartMode("difficulty"); setReviewedSongs(null); setStartError(""); }}
                className={`cursor-pointer rounded-full px-5 py-2 font-display text-xs ${startMode === "difficulty" ? "bg-accent text-accent-foreground" : "border-2 border-border text-muted-foreground"}`}
              >
                Difficulty
              </button>
              <button
                type="button"
                onClick={() => { setStartMode("custom"); setStartError(""); }}
                className={`cursor-pointer rounded-full px-5 py-2 font-display text-xs ${startMode === "custom" ? "bg-accent text-accent-foreground" : "border-2 border-border text-muted-foreground"}`}
              >
                Custom
              </button>
            </div>
            {startMode === "difficulty" ? (
              <div className="space-y-4">
                <div className="flex items-center gap-3">
                  <label className="flex items-center gap-2 text-[13px] text-muted-foreground">
                    Difficulty
                    <select value={selectedTier} onChange={(event) => setSelectedTier(event.target.value as DifficultyTier)} className="rounded-full border-2 border-border bg-background px-3 py-1.5 text-sm font-semibold text-foreground outline-none">
                      {DIFFICULTY_TIERS.map((tier) => (
                        <option key={tier} value={tier}>{tier.charAt(0) + tier.slice(1).toLowerCase()}</option>
                      ))}
                    </select>
                  </label>
                  <label className="flex items-center gap-2 text-[13px] text-muted-foreground">
                    Cards
                    <input type="number" min={MINIMUM_TARGET_CARD_COUNT} value={targetCardCount} onChange={(event) => setTargetCardCount(Math.max(MINIMUM_TARGET_CARD_COUNT, Number(event.target.value)))} className="w-20 rounded-full border-2 border-border bg-background px-3 py-1.5 text-right text-sm font-semibold text-foreground outline-none" />
                  </label>
                  <button type="button" onClick={handleGenerateSet} disabled={generateSet.isPending} className="rounded-full bg-primary px-4 py-2 font-display text-xs text-primary-foreground disabled:opacity-60">
                    {generateSet.isPending ? "Generating" : "Generate"}
                  </button>
                </div>
                {reviewedSongs ? (
                  <div>
                    <p className="mb-2 text-[13px] text-muted-foreground">Review the set, then confirm to start.</p>
                    <ul className="max-h-48 space-y-1.5 overflow-y-auto pr-1">
                      {reviewedSongs.map((preview) => (
                        <li key={preview.id} className="flex items-baseline justify-between gap-3 rounded-xl border-2 border-border bg-background px-3 py-2">
                          <span className="truncate text-sm font-semibold text-foreground">{preview.title} <span className="font-normal text-muted-foreground">{preview.artists.join(", ")}</span></span>
                          <span className="shrink-0 font-display text-sm text-accent">{preview.releaseYear}</span>
                        </li>
                      ))}
                    </ul>
                    <button type="button" onClick={handleConfirmGeneratedSet} disabled={startWithSongs.isPending} className="mt-3 inline-flex items-center gap-2 rounded-full bg-accent px-5 py-2.5 font-display text-xs text-accent-foreground disabled:opacity-60">
                      {startWithSongs.isPending ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4 fill-current" />}
                      Confirm and start
                    </button>
                  </div>
                ) : null}
              </div>
            ) : (
              <div className="space-y-4">
                <div>
                  <p className="mb-2 text-[13px] text-muted-foreground">Start from one of your playlists</p>
                  <div className="grid max-h-40 gap-2 overflow-y-auto pr-1">
                    {playlistsQuery.data?.map((playlist) => {
                      const isSelected = selectedCustomPlaylistId === playlist.id;
                      return (
                        <button
                          key={playlist.id}
                          type="button"
                          onClick={() => setSelectedCustomPlaylistId(playlist.id)}
                          className={`flex items-center justify-between rounded-xl border-2 px-3 py-2 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${isSelected ? "border-primary bg-primary/10" : "border-border bg-background hover:border-primary/60"}`}
                        >
                          <span>
                            <span className="block text-sm font-semibold text-foreground">{playlist.name}</span>
                            <span className="text-xs text-muted-foreground">{playlist.songCount} songs</span>
                          </span>
                          {isSelected ? <Check className="size-4 shrink-0 text-primary" /> : null}
                        </button>
                      );
                    })}
                  </div>
                  <button type="button" onClick={handleStartCustomPlaylist} disabled={selectedCustomPlaylistId === undefined || startCustom.isPending} className="mt-3 inline-flex items-center gap-2 rounded-full bg-accent px-5 py-2.5 font-display text-xs text-accent-foreground disabled:cursor-not-allowed disabled:opacity-60">
                    {startCustom.isPending ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4 fill-current" />}
                    Start from playlist
                  </button>
                </div>
                <div>
                  <p className="mb-2 text-[13px] text-muted-foreground">Or paste a playlist link</p>
                  <div className="flex gap-2">
                    <input type="text" value={playlistLink} onChange={(event) => setPlaylistLink(event.target.value)} placeholder="YouTube playlist link or id" className="min-w-0 flex-1 rounded-full border-2 border-border bg-background px-4 py-2 text-sm text-foreground outline-none placeholder:text-muted-foreground" />
                    <button type="button" onClick={handleStartPlaylistLink} disabled={!playlistLink.trim() || startCustom.isPending} className="shrink-0 rounded-full bg-accent px-4 py-2 font-display text-xs text-accent-foreground disabled:cursor-not-allowed disabled:opacity-60">
                      Start
                    </button>
                  </div>
                </div>
              </div>
            )}
            {startError ? <p role="alert" className="mt-4 text-sm text-destructive">{startError}</p> : null}
          </section>
        ) : null}
        {isChatOpen ? (
          <GroupChatOverlay
            groupId={groupId}
            connectionState={groupRealtime.connectionState}
            sendChat={groupRealtime.sendChat}
            onClose={() => setIsChatOpen(false)}
          />
        ) : null}
      </section>

      <footer className="flex flex-wrap items-center gap-3">
        {!isCurrentUserLoading && isCurrentUserAdmin ? (
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
        {!isCurrentUserLoading && isCurrentUserAdmin ? (
          <button
            type="button"
            onClick={openStartOptions}
            disabled={groupQuery.data.status !== "OPEN"}
            className="inline-flex items-center gap-2 rounded-full border-2 border-border bg-card px-5 py-3 text-[13px] font-semibold text-card-foreground disabled:cursor-not-allowed disabled:opacity-60"
          >
            Custom start
          </button>
        ) : null}
          <button
            type="button"
            onClick={() => setIsChatOpen((currentValue) => !currentValue)}
            className={`inline-flex items-center gap-2 rounded-full border-2 bg-card px-5 py-3 text-[13px] font-semibold text-card-foreground ${isChatOpen ? "border-primary text-primary" : "border-border"}`}
          >
          <MessageCircle className="size-4" />
          Chat
        </button>
        {!isCurrentUserLoading && isCurrentUserAdmin ? (
          <button
            type="button"
            onClick={openSettings}
            className="inline-flex items-center gap-2 rounded-full border-2 border-border bg-card px-5 py-3 text-[13px] font-semibold text-card-foreground"
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
        <span className="inline-flex items-center gap-1 text-[10px] text-muted-foreground" aria-live="polite">
          <span className={`size-1.5 rounded-full ${groupRealtime.connectionState === "connected" ? "bg-green" : "bg-warning"}`} />
          {groupRealtime.connectionState === "connected" ? "Live" : "Reconnecting"}
        </span>
      </footer>
    </main>
  );
}
