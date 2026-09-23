"use client";

import { use, useCallback, useEffect, useMemo, useState, Suspense } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { toast } from "sonner";
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
  useStartSessionWithSongs,
} from "@/hooks/generated/group-management/group-management";
import type { GenerateDifficultySetRequestTier } from "@/hooks/models/generateDifficultySetRequestTier";
import type { GeneratedSongPreviewDTO } from "@/hooks/models/generatedSongPreviewDTO";
import type { MemberDTO } from "@/hooks/models/memberDTO";
import { useQueryClient } from "@tanstack/react-query";
import { copyText } from "@/lib/clipboard";
import { playlistTitleColor } from "@/lib/playlist-colors";
import { GroupChatOverlay } from "@/components/group-chat-overlay";
import { PlaylistCoverMosaic } from "@/components/playlist-cover-mosaic";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/shadcn/alert-dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/shadcn/select";
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
const MINIMUM_PLAYERS_TO_START = 2;
const LOBBY_FLOAT_STAGGER_CYCLE = 5;
const LOBBY_FLOAT_STAGGER_SECONDS = 1.1;
const MINIMUM_TARGET_CARD_COUNT = 1;
const DIFFICULTY_CARD_HEADROOM_MULTIPLIER = 3;
const DIFFICULTY_TIERS: GenerateDifficultySetRequestTier[] = ["EASY", "MEDIUM", "HARD"];

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
  const floatDelay = `${(index % LOBBY_FLOAT_STAGGER_CYCLE) * -LOBBY_FLOAT_STAGGER_SECONDS}s`;

  return (
    <div className={`absolute ${orbitPosition} lobby-float flex flex-col items-center`} style={{ animationDelay: floatDelay }}>
      <div className="relative">
        <div
          className={`avatar-initial flex size-[76px] items-center justify-center rounded-full font-display text-2xl shadow-[0_0_0_3px_var(--background),0_0_0_8px_var(--green)] sm:size-[108px] sm:text-[34px] ${colorClass} ${
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

export default function GroupLobbyPage({ params }: PageProps) {
  const { groupId: groupIdParam } = use(params);
  const groupId = Number(groupIdParam);
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const [copyFeedback, setCopyFeedback] = useState("");
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isTierPopupOpen, setIsTierPopupOpen] = useState(false);
  const [isCustomPickerOpen, setIsCustomPickerOpen] = useState(false);
  const [isMinPlayersOpen, setIsMinPlayersOpen] = useState(false);
  const [isChatOpen, setIsChatOpen] = useState(false);
  const [selectedTier, setSelectedTier] = useState<GenerateDifficultySetRequestTier>("MEDIUM");
  const [playlistSource, setPlaylistSource] = useState<"tier" | "custom">("tier");
  const [reviewedSongs, setReviewedSongs] = useState<GeneratedSongPreviewDTO[] | null>(null);
  const [startError, setStartError] = useState("");
  const [selectedDjMode, setSelectedDjMode] = useState<"FIXED" | "ROTATING">("ROTATING");
  const [selectedPlaylistIds, setSelectedPlaylistIds] = useState<number[]>([]);
  const [selectedFixedDjMemberId, setSelectedFixedDjMemberId] = useState<number | undefined>(undefined);
  const [winCondition, setWinCondition] = useState(MINIMUM_WIN_CONDITION);
  const groupQuery = useGetGroup(groupId, { query: { retry: false } });
  const groupRealtime = useGroupRealtime(groupId);
  const { data: currentUser } = useGetCurrentUser();
  const playlistsQuery = useGetUserPlaylists({ query: { retry: false } });
  const startSession = useStartGameSession();
  const generateSet = useGenerateDifficultySet();
  const startWithSongs = useStartSessionWithSongs();
  const leaveGroup = useLeaveGroup();
  const updateSettings = useUpdateGroupSettings();
  const activeSessionQuery = useGetActiveSessionForGroup(groupId, {
    query: { enabled: groupQuery.data?.status === "LOCKED", retry: false },
  });

  const members = useMemo(() => groupQuery.data?.members ?? [], [groupQuery.data?.members]);
  const currentMember = members.find((member) => member.userId === currentUser?.id);
  const isCurrentUserAdmin = Boolean(currentMember?.isAdmin);
  const isCurrentUserLoading = currentUser === undefined;
  const groupPlaylists = groupQuery.data?.playlists ?? [];
  const featuredPlaylist = groupPlaylists[0];
  const playlistChipLabel = groupPlaylists.length === 0
    ? "No playlist selected"
    : groupPlaylists.length === 1
      ? (featuredPlaylist?.name ?? "No playlist selected")
      : `${groupPlaylists.length} playlists`;
  const selectedPlaylistNames = (playlistsQuery.data ?? [])
    .filter((playlist) => selectedPlaylistIds.includes(playlist.id))
    .map((playlist) => playlist.name)
    .join(", ");

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

  function closeAllPopups() {
    setIsSettingsOpen(false);
    setIsTierPopupOpen(false);
    setIsCustomPickerOpen(false);
    setIsChatOpen(false);
  }

  async function copyInviteLink() {
    const inviteCode = groupQuery.data?.inviteCode;
    if (!inviteCode) {
      return;
    }

    await copyText(`${window.location.origin}/groups/join/${inviteCode}`);
    setCopyFeedback("Invite link copied");
  }

  function handleStartGame() {
    if (members.length < MINIMUM_PLAYERS_TO_START) {
      setIsMinPlayersOpen(true);
      return;
    }
    startSession.mutate(
      { groupId },
      {
        onSuccess: () => { refreshGroup(); refreshActiveMembership(); closeAllPopups(); },
        onError: (error) => toast.error(mutationErrorMessage(error)),
      },
    );
  }

  function openTierPopup() {
    setReviewedSongs(null);
    setStartError("");
    closeAllPopups();
    setIsTierPopupOpen(true);
  }

  function closeTierPopup() {
    setIsTierPopupOpen(false);
    setReviewedSongs(null);
    setStartError("");
  }

  function openCustomPicker() {
    setSelectedPlaylistIds(
      (groupQuery.data?.playlists ?? [])
        .map((playlist) => playlist.id)
        .filter((id): id is number => id !== undefined),
    );
    setStartError("");
    closeAllPopups();
    setIsTierPopupOpen(false);
    setIsCustomPickerOpen(true);
  }

  function closeCustomPicker() {
    setIsCustomPickerOpen(false);
    setStartError("");
  }

  function backToTiers() {
    setIsCustomPickerOpen(false);
    setIsSettingsOpen(false);
    setIsChatOpen(false);
    setIsTierPopupOpen(true);
  }

  const applyPlaylistPreselect = useCallback((playlistId: number | null) => {
    if (playlistId === null) {
      return;
    }
    setSelectedPlaylistIds([playlistId]);
    setStartError("");
    closeAllPopups();
    setIsTierPopupOpen(false);
    setIsCustomPickerOpen(true);
    const params = new URLSearchParams(window.location.search);
    params.delete("playlist");
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }, [pathname, router]);

  function handleModeStartSuccess() {
    refreshGroup();
    refreshActiveMembership();
    closeAllPopups();
  }

  function handleGenerateSet() {
    setStartError("");
    const computedCardCount = Math.max(
      MINIMUM_TARGET_CARD_COUNT,
      members.length * (groupQuery.data?.winConditionCardCount ?? MINIMUM_WIN_CONDITION) * DIFFICULTY_CARD_HEADROOM_MULTIPLIER,
    );
    generateSet.mutate(
      { groupId, data: { tier: selectedTier, targetCardCount: computedCardCount } },
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
      {
        groupId,
        data: {
          songIds: reviewedSongs
            .map((preview) => preview.id)
            .filter((id): id is number => id !== undefined),
        },
      },
      {
        onSuccess: handleModeStartSuccess,
        onError: (error) => setStartError(mutationErrorMessage(error)),
      },
    );
  }

  function handleConfirmCustomSelection() {
    if (selectedPlaylistIds.length === 0) {
      return;
    }
    setStartError("");
    updateSettings.mutate(
      { groupId, data: { playlistIds: selectedPlaylistIds } },
      {
        onSuccess: () => {
          refreshGroup();
          setPlaylistSource("custom");
          setIsCustomPickerOpen(false);
        },
        onError: (error) => setStartError(mutationErrorMessage(error)),
      },
    );
  }

  function handleLeaveLobby() {
    leaveGroup.mutate(
      { groupId },
      {
        onSuccess: () => { refreshActiveMembership(); router.push("/playlists"); },
        onError: (error) => toast.error(mutationErrorMessage(error)),
      },
    );
  }

  function openSettings() {
    setSelectedDjMode(groupQuery.data?.djMode ?? "ROTATING");
    setSelectedFixedDjMemberId(groupQuery.data?.fixedDjMemberId ?? undefined);
    setWinCondition(groupQuery.data?.winConditionCardCount ?? MINIMUM_WIN_CONDITION);
    closeAllPopups();
    setIsSettingsOpen(true);
  }

  function toggleChat() {
    if (isChatOpen) {
      setIsChatOpen(false);
      return;
    }
    closeAllPopups();
    setIsChatOpen(true);
  }

  function saveSettings() {
    updateSettings.mutate(
      {
        groupId,
        data: {
          djMode: selectedDjMode,
          winConditionCardCount: winCondition,
          fixedDjMemberId: selectedFixedDjMemberId,
        },
      },
      {
        onSuccess: () => {
          refreshGroup();
          setIsSettingsOpen(false);
          toast.success("Settings saved");
        },
        onError: () => toast.error("Couldn't save settings"),
      },
    );
  }

  function togglePlaylistSelected(playlistId: number | undefined) {
    if (playlistId === undefined) {
      return;
    }
    setSelectedPlaylistIds((currentIds) =>
      currentIds.includes(playlistId)
        ? currentIds.filter((id) => id !== playlistId)
        : [...currentIds, playlistId],
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
        {isCurrentUserAdmin ? (
          <div className="relative w-fit">
            <button
              type="button"
              onClick={openTierPopup}
              title="Choose playlists"
              aria-label="Choose playlists"
              className="inline-flex w-fit cursor-pointer items-center gap-2.5 rounded-full border-2 border-border bg-card py-2 pl-2 pr-4 transition-colors hover:border-primary/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <span
                className="size-[26px] rounded-[8px]"
                style={{ backgroundColor: featuredPlaylist ? `#${featuredPlaylist.color}` : "var(--primary)" }}
              />
              <span className="font-semibold text-[13px] text-card-foreground">
                {playlistChipLabel}
              </span>
              {featuredPlaylist ? (
                <span className="text-xs text-muted-foreground">{featuredPlaylist.songCount} songs</span>
              ) : null}
            </button>
            {isTierPopupOpen ? (
              <>
                <button type="button" aria-label="Close start options" onClick={closeTierPopup} className="fixed inset-0 z-10 cursor-default" />
                <section aria-label="Start options" className="absolute right-0 top-full z-20 mt-2 w-[320px] rounded-[18px] border-[3px] border-border bg-card p-5 shadow-[6px_6px_0_rgba(0,0,0,0.35)]">
                  <div className="mb-4 flex flex-wrap gap-2">
                    {DIFFICULTY_TIERS.map((tier) => (
                      <button
                        key={tier}
                        type="button"
                        onClick={() => { setSelectedTier(tier); setPlaylistSource("tier"); setReviewedSongs(null); setStartError(""); }}
                        aria-pressed={playlistSource === "tier" && selectedTier === tier}
                        className={`cursor-pointer rounded-full px-4 py-2 font-display text-xs ${playlistSource === "tier" && selectedTier === tier ? "bg-accent text-accent-foreground" : "border-2 border-border text-muted-foreground"}`}
                      >
                        {tier.charAt(0) + tier.slice(1).toLowerCase()}
                      </button>
                    ))}
                    <button
                      type="button"
                      onClick={openCustomPicker}
                      aria-pressed={playlistSource === "custom"}
                      className={`cursor-pointer rounded-full px-4 py-2 font-display text-xs ${playlistSource === "custom" ? "bg-accent text-accent-foreground" : "border-2 border-border text-muted-foreground"}`}
                    >
                      Custom
                    </button>
                  </div>
                  <div className="flex items-center gap-3">
                    <button type="button" onClick={handleGenerateSet} disabled={generateSet.isPending} className="rounded-full bg-primary px-4 py-2 font-display text-xs text-primary-foreground disabled:opacity-60">
                      {generateSet.isPending ? "Generating" : "Generate"}
                    </button>
                  </div>
                  {reviewedSongs ? (
                    <div className="mt-4">
                      <p className="mb-2 text-[13px] text-muted-foreground">Review the set, then confirm to start.</p>
                      <ul className="max-h-48 space-y-1.5 overflow-y-auto pr-1">
                        {reviewedSongs.map((preview) => (
                          <li key={preview.id} className="flex items-baseline justify-between gap-3 rounded-xl border-2 border-border bg-background px-3 py-2">
                            <span className="truncate text-sm font-semibold text-foreground">{preview.title} <span className="font-normal text-muted-foreground">{(preview.artists ?? []).join(", ")}</span></span>
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
                  {startError ? <p role="alert" className="mt-4 text-sm text-destructive">{startError}</p> : null}
                </section>
              </>
            ) : null}
          </div>
        ) : (
          <div className="inline-flex w-fit items-center gap-2.5 rounded-full border-2 border-border bg-card py-2 pl-2 pr-4">
            <span
              className="size-[26px] rounded-[8px]"
              style={{ backgroundColor: featuredPlaylist ? `#${featuredPlaylist.color}` : "var(--primary)" }}
            />
            <span className="font-semibold text-[13px] text-card-foreground">
              {playlistChipLabel}
            </span>
            {featuredPlaylist ? (
              <span className="text-xs text-muted-foreground">{featuredPlaylist.songCount} songs</span>
            ) : null}
          </div>
        )}
      </header>

      <section className="relative flex min-h-0 flex-1 items-center justify-center overflow-y-auto py-10">
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
          <>
            <button type="button" aria-label="Close settings" onClick={() => setIsSettingsOpen(false)} className="fixed inset-0 z-10 cursor-default" />
            <section aria-label="Group settings" className="absolute bottom-3 left-0 z-20 w-full max-w-[400px] rounded-[18px] border-[3px] border-border bg-card p-6 shadow-[6px_6px_0_rgba(0,0,0,0.35)] sm:left-[228px] sm:p-7">
              <div className="mb-5 flex items-center justify-between">
                <h2 className="font-display text-lg text-card-foreground">Settings</h2>
                <button type="button" onClick={() => setIsSettingsOpen(false)} className="text-muted-foreground hover:text-card-foreground">Close</button>
              </div>
              <div className="space-y-4">
                <div className="flex items-center justify-between gap-3">
                  <span id="dj-mode-label" className="text-[13px] text-muted-foreground">DJ mode</span>
                  <Select value={selectedDjMode} onValueChange={(value) => setSelectedDjMode(value as "FIXED" | "ROTATING")}>
                    <SelectTrigger aria-labelledby="dj-mode-label" className="w-[140px] rounded-full border-2 border-border bg-background">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ROTATING">Rotating</SelectItem>
                      <SelectItem value="FIXED">Fixed</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                {selectedDjMode === "FIXED" ? (
                  <div className="flex items-center justify-between gap-3">
                    <span id="fixed-dj-label" className="text-[13px] text-muted-foreground">Fixed DJ</span>
                    <Select value={selectedFixedDjMemberId === undefined ? "auto" : String(selectedFixedDjMemberId)} onValueChange={(value) => setSelectedFixedDjMemberId(value === "auto" ? undefined : Number(value))}>
                      <SelectTrigger aria-labelledby="fixed-dj-label" className="w-[140px] rounded-full border-2 border-border bg-background">
                        <SelectValue placeholder="First to join" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="auto">First to join</SelectItem>
                        {members.map((member) => (
                          <SelectItem key={member.id} value={String(member.id ?? "")}>{member.displayName || "Player"}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                ) : null}
                <label className="flex items-center justify-between gap-3 text-[13px] text-muted-foreground">
                  Cards to win
                  <input type="number" min={MINIMUM_WIN_CONDITION} value={winCondition} onChange={(event) => setWinCondition(Math.max(MINIMUM_WIN_CONDITION, Number(event.target.value)))} className="w-20 rounded-full border-2 border-border bg-background px-3 py-1.5 text-right text-sm font-semibold text-foreground outline-none" />
                </label>
              </div>
              <div className="mt-6 flex gap-2.5"><button type="button" onClick={saveSettings} disabled={updateSettings.isPending} className="rounded-full bg-primary px-4 py-2.5 font-display text-xs text-primary-foreground disabled:opacity-60">{updateSettings.isPending ? "Saving" : "Save changes"}</button><button type="button" onClick={() => setIsSettingsOpen(false)} className="rounded-full border-2 border-border px-4 py-2.5 text-xs font-semibold text-card-foreground">Close</button></div>
            </section>
          </>
        ) : null}
        {isCustomPickerOpen ? (
          <>
            <button type="button" aria-label="Close playlist selection" onClick={closeCustomPicker} className="absolute inset-0 z-20 cursor-default bg-background/80" />
            <section aria-label="Custom playlist selection" className="absolute inset-x-0 top-6 bottom-6 z-30 overflow-y-auto rounded-[18px] border-[3px] border-border bg-card p-6 shadow-[6px_6px_0_rgba(0,0,0,0.35)] sm:p-8">
              <div className="mb-5 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <button type="button" onClick={backToTiers} className="text-muted-foreground hover:text-card-foreground">Back</button>
                  <h2 className="font-display text-lg text-card-foreground">Choose playlists</h2>
                </div>
                <button type="button" onClick={closeCustomPicker} className="text-muted-foreground hover:text-card-foreground">Close</button>
              </div>
              <p className="mb-3 text-[13px] text-muted-foreground">
                {selectedPlaylistIds.length === 0 ? "No playlists selected" : `Chosen: ${selectedPlaylistNames}`}
              </p>
              <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
                {playlistsQuery.data?.map((playlist) => {
                  const isSelected = selectedPlaylistIds.includes(playlist.id);
                  return (
                    <button
                      key={playlist.id}
                      type="button"
                      onClick={() => togglePlaylistSelected(playlist.id)}
                      aria-pressed={isSelected}
                      className={`relative flex flex-col overflow-hidden rounded-2xl border-[3px] bg-card text-left shadow-lg transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${isSelected ? "border-primary" : "border-border-strong hover:border-primary/60"}`}
                    >
                      <PlaylistCoverMosaic
                        previewYoutubeIds={playlist.previewYoutubeIds ?? []}
                        className="w-full rounded-none border-none"
                      />
                      {isSelected ? (
                        <span className="absolute right-2 top-2 flex size-6 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-sm">
                          <Check className="size-3.5" />
                        </span>
                      ) : null}
                      <div className="flex flex-col gap-1.5 p-4 pb-[18px]">
                        <span
                          className="font-display text-base"
                          style={{ color: playlistTitleColor(playlist.color) }}
                        >
                          {playlist.name}
                        </span>
                        <span className="text-xs text-muted-foreground">{playlist.songCount} songs</span>
                      </div>
                    </button>
                  );
                })}
              </div>
              <div className="mt-6 flex gap-2.5">
                <button type="button" onClick={handleConfirmCustomSelection} disabled={selectedPlaylistIds.length === 0 || updateSettings.isPending} className="rounded-full bg-primary px-4 py-2.5 font-display text-xs text-primary-foreground disabled:cursor-not-allowed disabled:opacity-60">
                  {updateSettings.isPending ? "Saving" : "Confirm"}
                </button>
                <button type="button" onClick={closeCustomPicker} className="rounded-full border-2 border-border px-4 py-2.5 text-xs font-semibold text-card-foreground">Close</button>
              </div>
              {startError ? <p role="alert" className="mt-4 text-sm text-destructive">{startError}</p> : null}
            </section>
          </>
        ) : null}
      </section>

      <div className="relative shrink-0">
      {isChatOpen ? (
        <GroupChatOverlay
          groupId={groupId}
          connectionState={groupRealtime.connectionState}
          sendChat={groupRealtime.sendChat}
          onClose={() => setIsChatOpen(false)}
          floating
        />
      ) : null}
      <footer className="flex shrink-0 flex-wrap items-center gap-3">
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
        <AlertDialog open={isMinPlayersOpen} onOpenChange={setIsMinPlayersOpen}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Not enough players</AlertDialogTitle>
              <AlertDialogDescription>
                Need at least {MINIMUM_PLAYERS_TO_START} players to start. Invite someone first.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogAction onClick={() => setIsMinPlayersOpen(false)}>Got it</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
          <button
            type="button"
            onClick={toggleChat}
            className={`relative z-20 inline-flex items-center gap-2 rounded-full border-2 bg-card px-5 py-3 text-[13px] font-semibold text-card-foreground ${isChatOpen ? "border-primary text-primary" : "border-border"}`}
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
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <button
              type="button"
              disabled={leaveGroup.isPending}
              className="inline-flex items-center gap-2 rounded-full border-2 border-destructive bg-transparent px-5 py-3 text-[13px] font-semibold text-destructive disabled:opacity-60"
            >
              {leaveGroup.isPending ? <Loader2 className="size-4 animate-spin" /> : <LogOut className="size-4" />}
              Leave lobby
            </button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Leave the lobby?</AlertDialogTitle>
              <AlertDialogDescription>
                You can rejoin anytime with the invite link or code while the group is still open.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction variant="destructive" onClick={handleLeaveLobby}>
                Leave
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </footer>
      </div>
    </main>
  );
}
