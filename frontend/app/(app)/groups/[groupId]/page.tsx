"use client";

import { use, useCallback, useEffect, useMemo, useRef, useState, Suspense } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { toast } from "sonner";
import {
  Check,
  Clipboard,
  Crown,
  Gauge,
  Loader2,
  LogOut,
  MessageCircle,
  Play,
  Settings,
  SlidersHorizontal,
  UserX,
} from "lucide-react";

import {
  getGetGroupQueryKey,
  getGetActiveMembershipQueryKey,
  useGetGroup,
  useLeaveGroup,
  useRemoveMember,
  usePromoteMember1,
  useStartGameSession,
  useUpdateGroupSettings,
} from "@/hooks/generated/group-management/group-management";
import { useGetCurrentUser, useGetUserPlaylists } from "@/hooks/generated/user-management/user-management";
import { useGetActiveSessionForGroup } from "@/hooks/generated/game-session/game-session";
import type { GenerateDifficultySetRequestTier } from "@/hooks/models/generateDifficultySetRequestTier";
import type { MemberDTO } from "@/hooks/models/memberDTO";
import { useQueryClient } from "@tanstack/react-query";
import { copyText } from "@/lib/clipboard";
import { playlistTitleColor } from "@/lib/playlist-colors";
import { GroupChatOverlay } from "@/components/group-chat-overlay";
import { GameStage } from "@/components/gameplay/game-stage";
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

interface OrbitSlotStyle {
  top: string;
  left: string;
  transform: string;
}

const TWO_PLAYER_ORBIT_SLOTS: OrbitSlotStyle[] = [
  { top: "calc(50% - 75px)", left: "calc(50% - 355px)", transform: "translate(-50%, -50%) rotate(-6deg)" },
  { top: "calc(50% + 140px)", left: "calc(50% + 340px)", transform: "translate(-50%, -50%) rotate(5deg)" },
];

const FOUR_PLAYER_ORBIT_SLOTS: OrbitSlotStyle[] = [
  { top: "calc(50% - 145px)", left: "calc(50% - 260px)", transform: "translate(-50%, -50%) rotate(-7deg)" },
  { top: "calc(50% - 195px)", left: "calc(50% + 245px)", transform: "translate(-50%, -50%) rotate(5deg)" },
  { top: "calc(50% + 180px)", left: "calc(50% - 210px)", transform: "translate(-50%, -50%) rotate(6deg)" },
  { top: "calc(50% + 160px)", left: "calc(50% + 295px)", transform: "translate(-50%, -50%) rotate(-5deg)" },
];

const EIGHT_PLAYER_ORBIT_SLOTS: OrbitSlotStyle[] = [
  { top: "calc(50% - 40px)", left: "calc(50% - 335px)", transform: "translate(-50%, -50%) rotate(-6deg)" },
  { top: "calc(50% - 215px)", left: "calc(50% - 245px)", transform: "translate(-50%, -50%) rotate(5deg)" },
  { top: "calc(50% - 260px)", left: "calc(50% + 20px)", transform: "translate(-50%, -50%) rotate(-4deg)" },
  { top: "calc(50% - 195px)", left: "calc(50% + 278px)", transform: "translate(-50%, -50%) rotate(6deg)" },
  { top: "calc(50% + 27px)", left: "calc(50% + 348px)", transform: "translate(-50%, -50%) rotate(-5deg)" },
  { top: "calc(50% + 213px)", left: "calc(50% + 240px)", transform: "translate(-50%, -50%) rotate(4deg)" },
  { top: "calc(50% + 265px)", left: "calc(50% - 20px)", transform: "translate(-50%, -50%) rotate(-6deg)" },
  { top: "calc(50% + 188px)", left: "calc(50% - 285px)", transform: "translate(-50%, -50%) rotate(5deg)" },
];

function getOrbitSlotStyle(slotIndex: number, totalMembers: number): OrbitSlotStyle {
  if (totalMembers <= 2) {
    return TWO_PLAYER_ORBIT_SLOTS[slotIndex % TWO_PLAYER_ORBIT_SLOTS.length];
  }
  if (totalMembers <= 4) {
    return FOUR_PLAYER_ORBIT_SLOTS[slotIndex % FOUR_PLAYER_ORBIT_SLOTS.length];
  }
  return EIGHT_PLAYER_ORBIT_SLOTS[slotIndex % EIGHT_PLAYER_ORBIT_SLOTS.length];
}

const LOADING_MEMBER_COUNT = 4;
const MINIMUM_WIN_CONDITION = 1;
const MINIMUM_PLAYERS_TO_START = 2;
const LOBBY_FLOAT_STAGGER_CYCLE = 5;
const LOBBY_FLOAT_STAGGER_SECONDS = 1.1;
const DIFFICULTY_TIERS: GenerateDifficultySetRequestTier[] = ["EASY", "MEDIUM", "HARD"];

function stableMemberNumber(member: MemberDTO): number {
  return member.userId ?? member.id ?? 0;
}

function stableMemberIndex(member: MemberDTO, collectionLength: number): number {
  return Math.abs(stableMemberNumber(member)) % collectionLength;
}

function compareLobbyMembers(firstMember: MemberDTO, secondMember: MemberDTO): number {
  const joinedAtComparison = (firstMember.joinedAt ?? "").localeCompare(secondMember.joinedAt ?? "");
  return joinedAtComparison === 0 ? stableMemberNumber(firstMember) - stableMemberNumber(secondMember) : joinedAtComparison;
}

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
  slotIndex,
  totalMembers,
  isCurrentUser,
  onRemove,
  onPromote,
}: {
  member: MemberDTO;
  slotIndex: number;
  totalMembers: number;
  isCurrentUser: boolean;
  onRemove?: (member: MemberDTO) => void;
  onPromote?: (member: MemberDTO) => void;
}) {
  const colorClass = MEMBER_COLORS[stableMemberIndex(member, MEMBER_COLORS.length)];
  const orbitSlotStyle = getOrbitSlotStyle(slotIndex, totalMembers);
  const floatDelay = `${stableMemberIndex(member, LOBBY_FLOAT_STAGGER_CYCLE) * -LOBBY_FLOAT_STAGGER_SECONDS}s`;
  const ringClass = member.isConnected
    ? "shadow-[0_0_0_3px_var(--background),0_0_0_8px_var(--green)]"
    : "shadow-[0_0_0_3px_var(--background),0_0_0_8px_var(--border)] opacity-50 grayscale";

  return (
    <div
      className="absolute flex flex-col items-center pointer-events-auto"
      style={{
        top: orbitSlotStyle.top,
        left: orbitSlotStyle.left,
        transform: orbitSlotStyle.transform,
      }}
    >
      <div className="lobby-float flex flex-col items-center" style={{ animationDelay: floatDelay }}>
        <div className="relative">
          {member.avatarUrl ? (
            <img
              src={member.avatarUrl}
              alt={`${member.displayName || "Player"} avatar`}
              className={`size-[108px] rounded-full object-cover ${ringClass}`}
            />
          ) : (
            <div
              className={`avatar-initial flex size-[108px] items-center justify-center rounded-full font-display text-[34px] ${colorClass} ${ringClass}`}
            >
              {memberInitial(member)}
            </div>
          )}
          {member.isAdmin ? (
            <Crown className="absolute -right-[10px] -top-[10px] size-[27px] fill-warning text-warning drop-shadow-[1px_1px_0_var(--background)]" />
          ) : null}
          {onRemove ? (
            <button
              type="button"
              onClick={() => onRemove(member)}
              aria-label={`Remove ${member.displayName || "Player"}`}
              className="absolute -left-2 -top-2 flex size-7 items-center justify-center rounded-full border-2 border-background bg-destructive text-white shadow-sm transition-transform hover:scale-110"
            >
              <UserX className="size-3.5" />
            </button>
          ) : null}
        </div>
        <span className="mt-[9px] max-w-[148px] truncate font-semibold text-[15px] text-foreground">
          {member.displayName || "Player"}
          {isCurrentUser ? " (you)" : ""}
        </span>
        {onPromote ? (
          <button
            type="button"
            onClick={() => onPromote(member)}
            aria-label={`Make ${member.displayName || "Player"} group admin`}
            className="mt-1 cursor-pointer text-xs font-semibold text-warning hover:underline"
          >
            Make group admin
          </button>
        ) : null}
        {!member.isConnected ? (
          <span className="mt-0.5 text-[10px] text-muted-foreground">Away</span>
        ) : null}
      </div>
    </div>
  );
}

function LobbyLoadingState() {
  return (
    <div className="relative flex min-h-0 flex-1 items-center justify-center overflow-hidden">
      {Array.from({ length: LOADING_MEMBER_COUNT }, (_, index) => {
        const slotStyle = getOrbitSlotStyle(index, LOADING_MEMBER_COUNT);
        return (
          <div
            key={index}
            className="absolute size-[108px] animate-pulse rounded-full bg-card"
            style={{
              top: slotStyle.top,
              left: slotStyle.left,
              transform: slotStyle.transform,
            }}
          />
        );
      })}
      <Loader2 className="size-8 animate-spin text-primary" aria-label="Loading group" />
    </div>
  );
}

// Runs only when the search params change. The callback is read through a ref, so a
// caller whose callback changes identity every render can't re-apply the same link.
function PlaylistPreselectCapture({ onCapture }: { onCapture: (playlistId: number | null) => void }) {
  const searchParams = useSearchParams();
  const onCaptureReference = useRef(onCapture);

  useEffect(() => {
    onCaptureReference.current = onCapture;
  });

  useEffect(() => {
    const rawPlaylistId = searchParams.get("playlist");
    if (rawPlaylistId === null) {
      onCaptureReference.current(null);
      return;
    }
    const parsedPlaylistId = Number(rawPlaylistId);
    onCaptureReference.current(Number.isInteger(parsedPlaylistId) && parsedPlaylistId > 0 ? parsedPlaylistId : null);
  }, [searchParams]);

  return null;
}

export default function GroupLobbyPage({ params }: PageProps) {
  return <GameStage>
    <GroupLobbyScreen params={params} />
  </GameStage>;
}

function GroupLobbyScreen({ params }: PageProps) {
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
  const [startError, setStartError] = useState("");
  const [startSessionError, setStartSessionError] = useState("");
  const [selectedDjMode, setSelectedDjMode] = useState<"FIXED" | "ROTATING">("ROTATING");
  const [selectedPlaylistIds, setSelectedPlaylistIds] = useState<number[]>([]);
  const [selectedFixedDjMemberId, setSelectedFixedDjMemberId] = useState<number | undefined>(undefined);
  const [winCondition, setWinCondition] = useState(MINIMUM_WIN_CONDITION);
  const groupQuery = useGetGroup(groupId, { query: { retry: false } });
  const groupRealtime = useGroupRealtime(groupId);
  const { data: currentUser } = useGetCurrentUser();
  const playlistsQuery = useGetUserPlaylists({ query: { retry: false } });
  const startSession = useStartGameSession();
  const leaveGroup = useLeaveGroup();
  const removeMember = useRemoveMember();
  const promoteMember = usePromoteMember1();
  const [memberPendingPromotion, setMemberPendingPromotion] = useState<MemberDTO | null>(null);
  const [memberPendingRemoval, setMemberPendingRemoval] = useState<MemberDTO | null>(null);
  const updateSettings = useUpdateGroupSettings();
  const activeSessionQuery = useGetActiveSessionForGroup(groupId, {
    query: { enabled: groupQuery.data?.status === "LOCKED", retry: false },
  });

  const members = useMemo(
    () => [...(groupQuery.data?.members ?? [])].sort(compareLobbyMembers),
    [groupQuery.data?.members],
  );
  const currentMember = members.find((member) => member.userId === currentUser?.id);
  const isCurrentUserAdmin = Boolean(currentMember?.isAdmin);
  const isCurrentUserLoading = currentUser === undefined;
  const groupPlaylists = groupQuery.data?.playlists ?? [];
  const featuredPlaylist = groupPlaylists[0];
  const savedTier = groupQuery.data?.difficultyTier;
  const storedFixedDjMember = members.find((member) => member.id === groupQuery.data?.fixedDjMemberId);
  const djModeSummary = groupQuery.data?.djMode === "ROTATING"
    ? "DJ: Rotating"
    : `DJ: Fixed, ${storedFixedDjMember?.displayName || "first to join"}`;
  const playlistChipLabel = savedTier ? `${savedTier.charAt(0)}${savedTier.slice(1).toLowerCase()} difficulty` : groupPlaylists.length === 0
    ? "No playlist selected"
    : groupPlaylists.length === 1
      ? (featuredPlaylist?.name ?? "No playlist selected")
      : `${groupPlaylists.length} playlists`;
  const selectedPlaylists = (playlistsQuery.data ?? []).filter((playlist) => selectedPlaylistIds.includes(playlist.id));
  const selectedPlaylistNames = selectedPlaylists.map((playlist) => playlist.name).join(", ");
  const selectedPlaylistSongCount = selectedPlaylists.reduce(
    (totalSongCount, playlist) => totalSongCount + (playlist.songCount ?? 0),
    0,
  );
  const groupPlaylistSongCount = groupPlaylists.reduce(
    (totalSongCount, playlist) => totalSongCount + (playlist.songCount ?? 0),
    0,
  );

  // The session query keeps its last result cached after the game ends, so only a
  // group that is still locked counts as being in a game.
  const isGroupInGame = groupQuery.data?.status === "LOCKED";
  useEffect(() => {
    if (isGroupInGame && activeSessionQuery.data?.id) {
      router.replace(`/sessions/${activeSessionQuery.data.id}`);
    }
  }, [activeSessionQuery.data?.id, isGroupInGame, router]);

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
    setStartSessionError("");
    startSession.mutate(
      { groupId },
      {
        onSuccess: () => { refreshGroup(); refreshActiveMembership(); closeAllPopups(); },
        onError: (error) => {
          const message = mutationErrorMessage(error);
          toast.error(message);
          setStartSessionError(message);
        },
      },
    );
  }

  function openTierPopup() {
    if (groupQuery.data?.difficultyTier) {
      setSelectedTier(groupQuery.data.difficultyTier);
    }
    setStartError("");
    closeAllPopups();
    setIsTierPopupOpen(true);
  }

  function closeTierPopup() {
    setIsTierPopupOpen(false);
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

  function handleConfirmTier() {
    setStartError("");
    updateSettings.mutate(
      { groupId, data: { difficultyTier: selectedTier } },
      {
        onSuccess: () => {
          refreshGroup();
          setPlaylistSource("tier");
          closeAllPopups();
        },
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

  function handleConfirmRemoval() {
    const memberId = memberPendingRemoval?.id;
    setMemberPendingRemoval(null);
    if (memberId === undefined) {
      return;
    }
    removeMember.mutate(
      { groupId, memberId },
      {
        onSuccess: refreshGroup,
        onError: (error) => toast.error(mutationErrorMessage(error)),
      },
    );
  }

  function handleConfirmPromotion() {
    const memberId = memberPendingPromotion?.id;
    if (memberId === undefined || !isCurrentUserAdmin || promoteMember.isPending) {
      return;
    }
    promoteMember.mutate({ groupId, memberId }, {
      onSuccess: (group) => {
        queryClient.setQueryData(getGetGroupQueryKey(groupId), group);
        refreshGroup();
        refreshActiveMembership();
        closeAllPopups();
        setMemberPendingPromotion(null);
        toast.success("Group admin transferred");
      },
      onError: (error) => toast.error(mutationErrorMessage(error)),
    });
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

  if (groupQuery.isLoading || isGroupInGame) {
    return (
      <main className="flex h-full min-h-0 flex-col overflow-hidden px-[56px] pt-[36px] pb-[40px]">
        <div className="h-10 w-52 animate-pulse rounded bg-card" />
        <LobbyLoadingState />
      </main>
    );
  }

  if (groupQuery.isError || !groupQuery.data) {
    return (
      <main className="flex h-full min-h-0 items-center justify-center overflow-hidden p-8">
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
    <main className="flex h-full min-h-0 flex-col overflow-hidden px-[56px] pt-[36px] pb-[40px]">
      <Suspense fallback={null}>
        <PlaylistPreselectCapture onCapture={applyPlaylistPreselect} />
      </Suspense>
      <header className="flex shrink-0 items-center justify-between">
        <h1 className="font-display text-[32px] text-foreground [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
          Group Lobby
        </h1>
        {isCurrentUserAdmin ? (
          <div className="relative w-fit">
            <button
              type="button"
              onClick={openTierPopup}
              title="Choose playlists"
              aria-label="Choose playlists"
              className="inline-flex w-fit cursor-pointer items-center gap-[10px] rounded-full border-2 border-border bg-card p-[8px_16px_8px_8px] transition-colors hover:border-primary/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <span
                className="size-[26px] rounded-[8px]"
                style={{ backgroundColor: featuredPlaylist ? `#${featuredPlaylist.color}` : "var(--primary)" }}
              />
              <span className="font-semibold text-[13px] text-card-foreground">
                {playlistChipLabel}
              </span>
              {featuredPlaylist ? (
                <>
                  <span className="size-1 rounded-full bg-muted-foreground/60" />
                  <span className="text-[12px] text-muted-foreground">{savedTier ? "Selected at start" : `${groupPlaylistSongCount} songs`}</span>
                </>
              ) : null}
            </button>
            {isTierPopupOpen ? (
              <>
                <button type="button" aria-label="Close start options" onClick={closeTierPopup} className="fixed inset-0 z-10 cursor-default" />
                <section aria-label="Start options" className="absolute right-0 top-full z-20 mt-3 w-[480px] rounded-[18px] border-[3px] border-border bg-card p-5 shadow-[6px_6px_0_var(--shadow-color)]">
                  <div className="flex items-center justify-between gap-2 font-display text-[12px]">
                    {DIFFICULTY_TIERS.map((tier) => (
                      <button
                        key={tier}
                        type="button"
                        onClick={() => { setSelectedTier(tier); setPlaylistSource("tier"); setStartError(""); }}
                        aria-pressed={playlistSource === "tier" && selectedTier === tier}
                        className={`inline-flex items-center gap-1 cursor-pointer whitespace-nowrap rounded-full border-2 px-3 py-2.5 font-display text-[12px] ${playlistSource === "tier" && selectedTier === tier ? "bg-primary text-primary-foreground border-primary" : "border-border text-muted-foreground"}`}
                      >
                        <Gauge className={`size-3.5 ${playlistSource === "tier" && selectedTier === tier ? "text-primary-foreground" : tier === "EASY" ? "text-green" : tier === "HARD" ? "text-destructive" : "text-warning"}`} />
                        {tier.charAt(0) + tier.slice(1).toLowerCase()}
                      </button>
                    ))}
                    <span aria-hidden="true" className="h-[28px] border-l border-border" />
                    <button
                      type="button"
                      onClick={openCustomPicker}
                      aria-pressed={playlistSource === "custom"}
                      className={`inline-flex items-center gap-1 cursor-pointer whitespace-nowrap rounded-full border-2 px-3 py-2.5 font-display text-[12px] ${playlistSource === "custom" ? "bg-primary text-primary-foreground border-primary" : "border-border text-muted-foreground"}`}
                    >
                      <SlidersHorizontal className="size-3.5" />
                      Custom
                    </button>
                  </div>
                  <button type="button" onClick={handleConfirmTier} disabled={updateSettings.isPending} className="mt-4 rounded-full bg-accent px-6 py-2.5 font-display text-[12px] text-accent-foreground disabled:opacity-60">
                    {updateSettings.isPending ? "Saving" : "Confirm"}
                  </button>
                  <p className="mt-2 text-[12px] text-muted-foreground">Songs are selected when the session starts.</p>
                  {startError ? <p role="alert" className="mt-3 text-sm text-destructive">{startError}</p> : null}
                </section>
              </>
            ) : null}
          </div>
        ) : (
          <div className="inline-flex w-fit items-center gap-[10px] rounded-full border-2 border-border bg-card p-[8px_16px_8px_8px]">
            <span
              className="size-[26px] rounded-[8px]"
              style={{ backgroundColor: featuredPlaylist ? `#${featuredPlaylist.color}` : "var(--primary)" }}
            />
            <span className="font-semibold text-[13px] text-card-foreground">
              {playlistChipLabel}
            </span>
            {featuredPlaylist ? (
              <>
                <span className="size-1 rounded-full bg-muted-foreground/60" />
                <span className="text-[12px] text-muted-foreground">{savedTier ? "Selected at start" : `${groupPlaylistSongCount} songs`}</span>
              </>
            ) : null}
          </div>
        )}
      </header>

      {/* The stage fills the space left over, so the footer actions stay on screen without page scrolling. */}
      <section className="relative flex min-h-0 flex-1 items-center justify-center overflow-hidden">
        <div className="z-10 text-center pointer-events-auto">
          <p className="mb-4 font-bold text-[12px] uppercase tracking-[3px] text-muted-foreground">
            Enter code to join
          </p>
          <p className="pl-[18px] font-display text-[76px] tracking-[18px] text-foreground [text-shadow:5px_5px_0_var(--text-shadow-on-page)]">
            {groupQuery.data.joinCode ?? "----"}
          </p>
          <button
            type="button"
            onClick={copyInviteLink}
            className="mt-5 inline-flex items-center gap-[9px] text-[13px] font-semibold text-muted-foreground transition-colors hover:text-foreground"
          >
            <Clipboard className="size-[15px]" />
            {copyFeedback || "Copy invite link"}
          </button>
        </div>

        {members.map((member, memberIndex) => (
          <LobbyMember
            key={member.id ?? stableMemberNumber(member)}
            member={member}
            slotIndex={memberIndex}
            totalMembers={members.length}
            isCurrentUser={member.userId === currentUser?.id}
            onRemove={isCurrentUserAdmin && member.userId !== currentUser?.id ? setMemberPendingRemoval : undefined}
            onPromote={isCurrentUserAdmin && member.userId !== currentUser?.id ? setMemberPendingPromotion : undefined}
          />
        ))}
        <AlertDialog
          open={memberPendingRemoval !== null}
          onOpenChange={(isOpen) => { if (!isOpen) setMemberPendingRemoval(null); }}
        >
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Remove {memberPendingRemoval?.displayName || "this player"}?</AlertDialogTitle>
              <AlertDialogDescription>
                They leave the lobby right away and can&apos;t rejoin this group.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction variant="destructive" onClick={handleConfirmRemoval}>
                Remove
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
        <AlertDialog open={memberPendingPromotion !== null} onOpenChange={(isOpen) => { if (!isOpen && !promoteMember.isPending) setMemberPendingPromotion(null); }}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Make {memberPendingPromotion?.displayName || "this player"} group admin?</AlertDialogTitle>
              <AlertDialogDescription>They will control group settings and membership. Your admin controls will be removed.</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel disabled={promoteMember.isPending}>Cancel</AlertDialogCancel>
              <button type="button" onClick={handleConfirmPromotion} disabled={promoteMember.isPending} className="cursor-pointer rounded-full bg-primary px-4 py-2 font-semibold text-primary-foreground disabled:opacity-60">
                {promoteMember.isPending ? "Transferring..." : "Transfer admin"}
              </button>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
        {isCustomPickerOpen ? (
          <>
            <button type="button" aria-label="Close playlist selection" onClick={closeCustomPicker} className="absolute inset-0 z-20 cursor-default bg-background/80" />
            <section aria-label="Custom playlist selection" className="absolute inset-x-0 top-6 bottom-6 z-30 overflow-y-auto rounded-[18px] border-[3px] border-border bg-card p-6 shadow-[6px_6px_0_var(--shadow-color)] sm:p-8">
              <div className="mb-5 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <button type="button" onClick={backToTiers} className="text-muted-foreground hover:text-card-foreground">Back</button>
                  <h2 className="font-display text-lg text-card-foreground">Choose playlists</h2>
                </div>
                <button type="button" onClick={closeCustomPicker} className="text-muted-foreground hover:text-card-foreground">Close</button>
              </div>
              <p className="mb-3 text-[13px] text-muted-foreground">
                {selectedPlaylistIds.length === 0 ? "No playlists selected" : `Chosen: ${selectedPlaylistNames} (${selectedPlaylistSongCount} songs)`}
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
      {isSettingsOpen ? (
        <>
          <button type="button" aria-label="Close settings" onClick={() => setIsSettingsOpen(false)} className="fixed inset-0 z-10 cursor-default" />
          <section aria-label="Group settings" className="absolute bottom-full left-[284px] z-20 mb-[12px] w-[400px] rounded-[18px] border-[3px] border-border bg-card/95 backdrop-blur-sm p-[26px_28px] shadow-[6px_6px_0_var(--shadow-color)]">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="font-display text-[18px] text-card-foreground">Settings</h2>
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
            <div className="mt-6 flex gap-2.5">
              <button type="button" onClick={saveSettings} disabled={updateSettings.isPending} className="rounded-full bg-accent px-4 py-2.5 font-display text-xs text-accent-foreground disabled:opacity-60">
                {updateSettings.isPending ? "Saving" : "Save changes"}
              </button>
              <button type="button" onClick={() => setIsSettingsOpen(false)} className="rounded-full border-2 border-border px-4 py-2.5 text-xs font-semibold text-card-foreground">
                Close
              </button>
            </div>
          </section>
        </>
      ) : null}
      {startSessionError ? (
        <p role="alert" className="mb-3 text-sm font-semibold text-destructive">
          Could not start the game: {startSessionError}
        </p>
      ) : null}
      <footer className="flex shrink-0 items-center gap-[14px]">
        {!isCurrentUserLoading && isCurrentUserAdmin ? (
          <button
            type="button"
            onClick={handleStartGame}
            disabled={startSession.isPending || groupQuery.data.status !== "OPEN"}
            className="inline-flex items-center gap-[9px] rounded-full bg-primary p-[13px_22px] font-display text-[13px] text-primary-foreground shadow-[4px_4px_0_var(--shadow-color)] disabled:cursor-not-allowed disabled:opacity-60"
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
          className={`relative z-20 inline-flex items-center gap-[9px] rounded-full border-2 bg-card p-[13px_22px] text-[13px] font-semibold ${isChatOpen ? "border-pink text-pink" : "border-border text-card-foreground"}`}
        >
          <MessageCircle className="size-4" />
          Chat
        </button>
        {!isCurrentUserLoading && isCurrentUserAdmin ? (
          <button
            type="button"
            onClick={openSettings}
            className="inline-flex items-center gap-[9px] rounded-full border-2 border-border bg-card p-[13px_22px] text-[13px] font-semibold text-card-foreground"
          >
            <Settings className="size-4" />
            Settings
          </button>
        ) : null}
        <span className="text-[12px] text-muted-foreground">{djModeSummary}</span>
        <div className="flex-1" />
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <button
              type="button"
              disabled={leaveGroup.isPending}
              className="inline-flex items-center gap-[9px] rounded-full border-2 border-destructive bg-transparent p-[13px_22px] text-[13px] font-semibold text-destructive disabled:opacity-60"
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
