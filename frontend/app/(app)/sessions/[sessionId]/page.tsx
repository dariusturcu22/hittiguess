"use client";

import { use, useCallback, useEffect, useRef, useState, type KeyboardEvent, type PointerEvent as ReactPointerEvent, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { ArrowDown, ArrowRight, Bell, Check, ExternalLink, Loader2, Lock, MessageCircle, Music2, TriangleAlert, UserRound, Volume2, VolumeX, X } from "lucide-react";

import { useGetCurrentRoundLinkOut, useGetSession } from "@/hooks/generated/game-session/game-session";
import { useGetGroup } from "@/hooks/generated/group-management/group-management";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import type { PlayerCardDTO } from "@/hooks/models/playerCardDTO";
import type { PlayerDTO } from "@/hooks/models/playerDTO";
import { PLACEMENT_PREVIEW_EVENT, useGameSessionRealtime, type SessionRoundEvent } from "@/hooks/use-game-session-realtime";
import { useAudioLevels } from "@/hooks/use-audio-levels";
import { useGroupRealtime } from "@/hooks/use-group-realtime";
import { requestDjTabAudioShare, useIsTabAudioShared } from "@/hooks/use-local-audio-stream";
import { YOUTUBE_LINK_OUT_REL, YOUTUBE_LINK_OUT_TARGET, youtubeLinkOutHref } from "@/lib/youtube-link-out";
import { playLockInSound } from "@/lib/lock-in-sound";
import {
  AWAITING_PLACEMENT_STATUS,
  BETTING_STATUS,
  COUNTDOWN_STATUS,
  describeGap,
  fractionRemaining,
  gapIndexForPointer,
  isGapCorrectForYear,
  isGapOpenForBet,
  isPlacementLocked,
  LOCK_IN_COUNTDOWN_MILLISECONDS,
  REVEAL_HOLD_MILLISECONDS,
  SCORED_STATUS,
  isRoundRevealed,
  predictNextTurn,
  secondsUntil,
} from "@/lib/gameplay-round";
import { GroupChatOverlay } from "@/components/group-chat-overlay";
import { Coin, MysteryCard, PlayerAvatar, QuestionCard, SongCard, TokenPile } from "@/components/gameplay/game-pieces";
import { GameplayTimeline, TIMELINE_CARD_SELECTOR } from "@/components/gameplay/gameplay-timeline";
import { RoundIntro } from "@/components/gameplay/round-intro";

const COMPLETED_SESSION_STATUS = "COMPLETED";
const FIRST_ROUND_NUMBER = 1;
const ROUND_INTRO_SECONDS = 3;
const ROUND_INTRO_SEEN_KEY_PREFIX = "hittiguess-round-intro-seen-";
const TURN_NOTICE_DURATION_MILLISECONDS = 4_000;
const CLOCK_TICK_MILLISECONDS = 250;
const DROP_ZONE_VERTICAL_REACH_PIXELS = 140;
const EDGE_SCROLL_ZONE_PIXELS = 72;
const EDGE_SCROLL_STEP_PIXELS = 14;
const DRAGGED_CARD_HALF_SIZE_PIXELS = 84;
// The dragged card floats above the pointer so the gap opening under it stays visible.
const DRAGGED_CARD_LIFT_PIXELS = 118;
const DRAGGED_COIN_HALF_SIZE_PIXELS = 26;
const SECONDS_PER_MINUTE = 60;
const PERCENT = 100;
const COUNTDOWN_RING_SIZE = 56;
const COUNTDOWN_RING_CENTER = COUNTDOWN_RING_SIZE / 2;
const COUNTDOWN_RING_RADIUS = 24;
const COUNTDOWN_RING_CIRCUMFERENCE = 2 * Math.PI * COUNTDOWN_RING_RADIUS;
const TWO_DIGITS = 2;
const ROUND_STARTED_EVENT = "ROUND_STARTED";
const NEXT_ROUND_EVENT = "NEXT_ROUND";

interface PageProps { params: Promise<{ sessionId: string }>; }

interface PointerDrag {
  pointerX: number;
  pointerY: number;
  hoverGap: number | null;
}

type Role = "active" | "dj" | "spectator";

function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / SECONDS_PER_MINUTE);
  const seconds = totalSeconds % SECONDS_PER_MINUTE;
  return `${minutes}:${String(seconds).padStart(TWO_DIGITS, "0")}`;
}

function readIntroSeen(sessionId: number): boolean {
  try {
    return window.sessionStorage.getItem(`${ROUND_INTRO_SEEN_KEY_PREFIX}${sessionId}`) !== null;
  } catch {
    return false;
  }
}

function markIntroSeen(sessionId: number) {
  try {
    window.sessionStorage.setItem(`${ROUND_INTRO_SEEN_KEY_PREFIX}${sessionId}`, "1");
  } catch {
    // Storage can be unavailable in private windows; the intro then simply replays.
  }
}

interface BetGapContext {
  placedPosition?: number;
  takenPositions: number[];
}

// The gap under the pointer, or null when the pointer is too far above or below the
// row (or, for a bet, over a gap that can't take one). Nudges the row sideways while the
// pointer rests near either edge, so a long timeline can be reached while dragging.
function hoverGapAt(track: HTMLDivElement | null, betGapContext: BetGapContext, clientX: number, clientY: number, isBetting: boolean): number | null {
  if (!track) return null;
  const trackRectangle = track.getBoundingClientRect();
  if (clientY < trackRectangle.top - DROP_ZONE_VERTICAL_REACH_PIXELS || clientY > trackRectangle.bottom + DROP_ZONE_VERTICAL_REACH_PIXELS) return null;
  if (clientX < trackRectangle.left + EDGE_SCROLL_ZONE_PIXELS) track.scrollBy({ left: -EDGE_SCROLL_STEP_PIXELS });
  if (clientX > trackRectangle.right - EDGE_SCROLL_ZONE_PIXELS) track.scrollBy({ left: EDGE_SCROLL_STEP_PIXELS });
  const cardCenters = Array.from(track.querySelectorAll(TIMELINE_CARD_SELECTOR)).map((element) => {
    const cardRectangle = element.getBoundingClientRect();
    return cardRectangle.left + cardRectangle.width / 2;
  });
  const gapIndex = gapIndexForPointer(clientX, cardCenters);
  if (isBetting && !isGapOpenForBet(gapIndex, betGapContext.placedPosition, betGapContext.takenPositions)) return null;
  return gapIndex;
}

function GapCaption({ verb, cards, gapIndex }: { verb: string; cards: PlayerCardDTO[]; gapIndex: number }) {
  const { before, beforeYear, after, afterYear } = describeGap(cards, gapIndex);
  if (before && after) return <>{verb} between <b className="text-foreground">{before}</b> ({beforeYear}) and <b className="text-foreground">{after}</b> ({afterYear})</>;
  if (after) return <>{verb} before <b className="text-foreground">{after}</b> ({afterYear})</>;
  if (before) return <>{verb} after <b className="text-foreground">{before}</b> ({beforeYear})</>;
  return <>{verb} on the timeline</>;
}

function StatusDot() {
  return <span className="size-1 shrink-0 rounded-full bg-muted-foreground" aria-hidden="true" />;
}

function RolePill({ label, name, colorIndex, size = "small" }: { label: string; name?: string; colorIndex: number; size?: "small" | "large" }) {
  return <div className="flex items-center gap-2">
    <PlayerAvatar name={name} colorIndex={colorIndex} className={size === "large" ? "size-10 text-sm" : "size-8 text-xs"} />
    <div className="text-left">
      <div className={`uppercase tracking-[1.5px] text-muted-foreground ${size === "large" ? "text-[9px]" : "text-[8px]"}`}>{label}</div>
      <div className={`font-bold text-card-foreground ${size === "large" ? "text-base" : "text-[13px]"}`}>{name ?? "Waiting"}</div>
    </div>
  </div>;
}

function DashedGap({ tone = "primary", isSmall = false, children }: { tone?: "primary" | "green" | "muted"; isSmall?: boolean; children?: ReactNode }) {
  const toneClasses = tone === "green" ? "border-green" : tone === "muted" ? "border-muted-foreground/60" : "border-primary";
  return <div data-timeline-focus="" className={`gameplay-gap-pulse flex h-[132px] items-center justify-center rounded-[22px] border-dashed sm:h-[168px] ${isSmall ? "w-16 rounded-2xl border-2" : "w-[118px] border-[3px] sm:w-[150px]"} ${toneClasses}`}>{children ?? <ArrowDown className={`size-5 ${tone === "green" ? "text-green" : tone === "muted" ? "text-muted-foreground" : "text-primary"}`} strokeWidth={2.5} />}</div>;
}

function BetCoin({ bettorName, colorIndex }: { bettorName?: string; colorIndex: number }) {
  return <div className="flex w-20 items-center justify-center" aria-label={`${bettorName ?? "A player"}'s bet`}>
    <div className="relative">
      <Coin size="large" />
      <PlayerAvatar name={bettorName} colorIndex={colorIndex} className="absolute -right-2 -top-2 size-[22px] border-2 border-background text-[9px]" />
    </div>
  </div>;
}

export default function GameSessionPage({ params }: PageProps) {
  const { sessionId: sessionIdParam } = use(params);
  const sessionId = Number(sessionIdParam);
  const router = useRouter();
  const sessionQuery = useGetSession(sessionId, { query: { retry: false } });
  const currentUserQuery = useGetCurrentUser();
  const session = sessionQuery.data;
  const groupRealtime = useGroupRealtime(session?.groupId ?? 0);
  const groupQuery = useGetGroup(session?.groupId ?? 0, { query: { enabled: Boolean(session?.groupId), retry: false } });
  const currentRound = session?.currentRound;
  const roundStatus = currentRound?.status;
  const players = [...(session?.players ?? [])].sort((first, second) => (first.turnOrder ?? 0) - (second.turnOrder ?? 0));
  const currentPlayer = players.find((player) => player.userId === currentUserQuery.data?.id);
  const isDj = currentPlayer?.id !== undefined && currentPlayer.id === currentRound?.djPlayerId;
  const isActivePlayer = currentPlayer?.id !== undefined && currentPlayer.id === currentRound?.activePlayerId;
  const role: Role = isActivePlayer ? "active" : isDj ? "dj" : "spectator";
  const linkOutQuery = useGetCurrentRoundLinkOut(sessionId, { query: { enabled: isDj && !isRoundRevealed(roundStatus), retry: false } });
  const isTabAudioShared = useIsTabAudioShared();
  const audioLevels = useAudioLevels();

  const [trackedRoundId, setTrackedRoundId] = useState<number | undefined>(undefined);
  const [placementDrag, setPlacementDrag] = useState<PointerDrag | null>(null);
  const [droppedGap, setDroppedGap] = useState<number | null>(null);
  const [betDrag, setBetDrag] = useState<PointerDrag | null>(null);
  const [stagedBetGap, setStagedBetGap] = useState<number | null>(null);
  const [previewGap, setPreviewGap] = useState<{ roundId?: number; position: number | null } | null>(null);
  const [isLinkOutOpen, setIsLinkOutOpen] = useState(false);
  const [artistGuess, setArtistGuess] = useState("");
  const [titleGuess, setTitleGuess] = useState("");
  const [feedbackMessage, setFeedbackMessage] = useState("");
  const [isChatOpen, setIsChatOpen] = useState(false);
  const [isTurnNoticeVisible, setIsTurnNoticeVisible] = useState(false);
  const [isIntroDismissed, setIsIntroDismissed] = useState(() => typeof window !== "undefined" && readIntroSeen(sessionId));
  const [now, setNow] = useState(() => Date.now());
  const trackReference = useRef<HTMLDivElement>(null);
  const submittedStagedBetRoundReference = useRef<number | undefined>(undefined);
  const lastPreviewReference = useRef<number | null | undefined>(undefined);

  if (trackedRoundId !== currentRound?.id) {
    setTrackedRoundId(currentRound?.id);
    setPlacementDrag(null);
    setDroppedGap(null);
    setBetDrag(null);
    setStagedBetGap(null);
    setPreviewGap(null);
    setIsLinkOutOpen(false);
    setFeedbackMessage("");
  }

  const handleRoundEvent = useCallback((event: SessionRoundEvent) => {
    if (event.type === PLACEMENT_PREVIEW_EVENT) {
      setPreviewGap({ roundId: event.payload?.roundId, position: event.payload?.position ?? null });
      return;
    }
    if (event.type === NEXT_ROUND_EVENT || event.type === ROUND_STARTED_EVENT) {
      setIsTurnNoticeVisible(true);
    }
  }, []);
  const realtime = useGameSessionRealtime(sessionId, handleRoundEvent);
  const { placeBet, previewPlacement } = realtime;

  useEffect(() => {
    if (session?.status === COMPLETED_SESSION_STATUS) router.replace(`/sessions/${sessionId}/results`);
  }, [router, session?.status, sessionId]);

  useEffect(() => {
    if (!isTurnNoticeVisible) return;
    const timeout = window.setTimeout(() => setIsTurnNoticeVisible(false), TURN_NOTICE_DURATION_MILLISECONDS);
    return () => window.clearTimeout(timeout);
  }, [isTurnNoticeVisible]);

  const isTimed = roundStatus === COUNTDOWN_STATUS || roundStatus === BETTING_STATUS || isRoundRevealed(roundStatus);
  useEffect(() => {
    if (!isTimed) return;
    const interval = window.setInterval(() => setNow(Date.now()), CLOCK_TICK_MILLISECONDS);
    return () => window.clearInterval(interval);
  }, [isTimed]);

  const activePlayer = players.find((player) => player.id === currentRound?.activePlayerId);
  const djPlayer = players.find((player) => player.id === currentRound?.djPlayerId);
  // Once a correct round is scored the card is already in the active player's timeline;
  // it's taken back out here so it keeps rendering in its revealed slot, and every gap
  // index (placement, bets) keeps meaning the same thing it did before scoring.
  const isRevealedCardInTimeline = roundStatus === SCORED_STATUS && currentRound?.placementCorrect === true && currentRound.placedPosition !== undefined;
  const fullTimeline = activePlayer?.timeline ?? [];
  const cards = isRevealedCardInTimeline ? fullTimeline.filter((_, cardIndex) => cardIndex !== currentRound?.placedPosition) : fullTimeline;
  const bets = currentRound?.bets ?? [];
  const myBet = bets.find((bet) => bet.playerId === currentPlayer?.id);
  const takenBetPositions = bets.flatMap((bet) => (bet.position === undefined ? [] : [bet.position]));
  const tokenCount = currentPlayer?.tokenCount ?? 0;
  const isAwaitingPlacement = roundStatus === AWAITING_PLACEMENT_STATUS;
  const canStakeBet = role === "spectator" && tokenCount > 0 && !myBet && (roundStatus === COUNTDOWN_STATUS || roundStatus === BETTING_STATUS);
  const activePlacementGap = placementDrag ? placementDrag.hoverGap : droppedGap;
  const colorIndexOf = (player?: PlayerDTO) => Math.max(0, players.findIndex((candidate) => candidate.id === player?.id));

  // Spectators watch the active player's drag; the active player publishes it.
  useEffect(() => {
    if (!isActivePlayer || !isAwaitingPlacement) {
      lastPreviewReference.current = undefined;
      return;
    }
    if (lastPreviewReference.current === activePlacementGap) return;
    if (previewPlacement(activePlacementGap)) lastPreviewReference.current = activePlacementGap;
  }, [activePlacementGap, isActivePlayer, isAwaitingPlacement, previewPlacement]);

  // A token staged during the countdown is placed the moment the betting window opens.
  useEffect(() => {
    if (roundStatus !== BETTING_STATUS || stagedBetGap === null || myBet || currentRound?.id === undefined) return;
    if (submittedStagedBetRoundReference.current === currentRound.id) return;
    submittedStagedBetRoundReference.current = currentRound.id;
    placeBet(stagedBetGap);
  }, [currentRound?.id, myBet, placeBet, roundStatus, stagedBetGap]);

  // Pointer handlers run outside render, so they read the round's open gaps from a ref.
  const betGapContextReference = useRef<BetGapContext>({ takenPositions: [] });
  useEffect(() => {
    betGapContextReference.current = { placedPosition: currentRound?.placedPosition, takenPositions: takenBetPositions };
  });


  const isPlacementDragging = placementDrag !== null;
  useEffect(() => {
    if (!isPlacementDragging) return;
    function movePlacement(event: PointerEvent) {
      setPlacementDrag({ pointerX: event.clientX, pointerY: event.clientY, hoverGap: hoverGapAt(trackReference.current, betGapContextReference.current, event.clientX, event.clientY, false) });
    }
    function dropPlacement(event: PointerEvent) {
      const gapIndex = hoverGapAt(trackReference.current, betGapContextReference.current, event.clientX, event.clientY, false);
      if (gapIndex !== null) setDroppedGap(gapIndex);
      setPlacementDrag(null);
    }
    window.addEventListener("pointermove", movePlacement);
    window.addEventListener("pointerup", dropPlacement);
    window.addEventListener("pointercancel", dropPlacement);
    return () => {
      window.removeEventListener("pointermove", movePlacement);
      window.removeEventListener("pointerup", dropPlacement);
      window.removeEventListener("pointercancel", dropPlacement);
    };
  }, [isPlacementDragging]);

  const isBetDragging = betDrag !== null;
  useEffect(() => {
    if (!isBetDragging) return;
    function moveBet(event: PointerEvent) {
      setBetDrag({ pointerX: event.clientX, pointerY: event.clientY, hoverGap: hoverGapAt(trackReference.current, betGapContextReference.current, event.clientX, event.clientY, true) });
    }
    function dropBet(event: PointerEvent) {
      const gapIndex = hoverGapAt(trackReference.current, betGapContextReference.current, event.clientX, event.clientY, true);
      setBetDrag(null);
      if (gapIndex === null) return;
      if (roundStatus === BETTING_STATUS) {
        if (!placeBet(gapIndex)) setFeedbackMessage("The game connection is not ready. Try again in a moment.");
      } else {
        setStagedBetGap(gapIndex);
      }
    }
    window.addEventListener("pointermove", moveBet);
    window.addEventListener("pointerup", dropBet);
    window.addEventListener("pointercancel", dropBet);
    return () => {
      window.removeEventListener("pointermove", moveBet);
      window.removeEventListener("pointerup", dropBet);
      window.removeEventListener("pointercancel", dropBet);
    };
  }, [isBetDragging, placeBet, roundStatus]);

  const dismissIntro = useCallback(() => {
    markIntroSeen(sessionId);
    setIsIntroDismissed(true);
  }, [sessionId]);

  if (!Number.isInteger(sessionId) || sessionId <= 0) return <main className="p-10 text-destructive">This game session link is invalid.</main>;
  if (sessionQuery.isLoading) return <main className="flex h-full min-h-[720px] items-center justify-center"><Loader2 className="size-8 animate-spin text-primary" aria-label="Loading game session" /></main>;
  if (sessionQuery.isError || !session) return <main className="p-10 text-destructive">This game session is unavailable.</main>;

  const roundNumber = currentRound?.roundNumber ?? session.currentRoundNumber ?? FIRST_ROUND_NUMBER;
  const winConditionCardCount = session.winConditionCardCount ?? 0;
  const isLocked = isPlacementLocked(roundStatus);
  const isRevealed = isRoundRevealed(roundStatus);
  const placedPosition = currentRound?.placedPosition;
  const spectatorPreviewGap = previewGap && previewGap.roundId === currentRound?.id ? previewGap.position : null;
  const playlists = groupQuery.data?.playlists ?? [];
  const playlistName = playlists.length === 1 ? playlists.at(0)?.name : playlists.length > 1 ? `${playlists.length} playlists` : "Auto-generated set";
  const playlistColor = playlists.at(0)?.color;
  const songCount = playlists.reduce((total, playlist) => total + playlist.songCount, 0);
  const showsIntro = !isIntroDismissed && roundNumber === FIRST_ROUND_NUMBER && isAwaitingPlacement;
  const turnNoticePlayer = isTurnNoticeVisible ? activePlayer : undefined;
  const winningBet = isRevealed && !currentRound?.placementCorrect
    ? bets.find((bet) => bet.position !== undefined && isGapCorrectForYear(cards, bet.position, currentRound?.revealedYear))
    : undefined;
  const cardWinner = isRevealed && currentRound?.placementCorrect ? activePlayer : players.find((player) => player.id === winningBet?.playerId);
  const nextTurn = predictNextTurn(players, currentRound?.activePlayerId, currentRound?.djPlayerId, session.djMode);

  function startPlacementDrag(event: ReactPointerEvent<HTMLElement>) {
    if (!isActivePlayer || !isAwaitingPlacement) return;
    event.preventDefault();
    setPlacementDrag({ pointerX: event.clientX, pointerY: event.clientY, hoverGap: droppedGap });
  }

  function moveDroppedCard(event: KeyboardEvent<HTMLElement>) {
    if (!isActivePlayer || !isAwaitingPlacement) return;
    const lastGap = cards.length;
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      setDroppedGap((gapIndex) => gapIndex ?? lastGap);
    } else if (event.key === "ArrowLeft") {
      event.preventDefault();
      setDroppedGap((gapIndex) => Math.max(0, (gapIndex ?? lastGap) - 1));
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      setDroppedGap((gapIndex) => Math.min(lastGap, (gapIndex ?? lastGap) + 1));
    }
  }

  function startBetDrag(event: ReactPointerEvent<HTMLElement>) {
    if (!canStakeBet) return;
    event.preventDefault();
    setBetDrag({ pointerX: event.clientX, pointerY: event.clientY, hoverGap: stagedBetGap });
  }

  function lockIn() {
    if (droppedGap === null) return;
    if (realtime.placeCard(droppedGap)) {
      playLockInSound();
      setFeedbackMessage("");
    } else {
      setFeedbackMessage("The game connection is not ready. Try again in a moment.");
    }
  }

  function submitGuess() {
    if (!artistGuess.trim() && !titleGuess.trim()) return;
    if (realtime.submitGuess(artistGuess, titleGuess)) {
      setArtistGuess("");
      setTitleGuess("");
      setFeedbackMessage("Guess sent. Keep listening for the result.");
    } else {
      setFeedbackMessage("The game connection is not ready. Try again in a moment.");
    }
  }

  function skipBetting() {
    if (!realtime.skipBetting()) setFeedbackMessage("The game connection is not ready. Try again in a moment.");
  }

  // --- Timeline slots: what sits in each gap this moment --------------------------
  const slots = new Map<number, ReactNode>();
  if (isAwaitingPlacement && isActivePlayer) {
    if (placementDrag?.hoverGap !== null && placementDrag?.hoverGap !== undefined) slots.set(placementDrag.hoverGap, <DashedGap />);
    else if (!placementDrag && droppedGap !== null) slots.set(droppedGap, <div data-timeline-focus=""><MysteryCard isInteractive levels={audioLevels} label="Your card. Choose a timeline position." onPointerDown={startPlacementDrag} onKeyDown={moveDroppedCard} /></div>);
  }
  if (isAwaitingPlacement && !isActivePlayer && spectatorPreviewGap !== null) {
    slots.set(spectatorPreviewGap, <DashedGap tone="muted"><MysteryCard size="small" className="gameplay-wobble scale-90" /></DashedGap>);
  }
  if (isLocked && !isRevealed && placedPosition !== undefined) slots.set(placedPosition, <div data-timeline-focus=""><QuestionCard /></div>);
  if (isRevealed && placedPosition !== undefined) {
    slots.set(placedPosition, <div data-timeline-focus="" className="relative">
      <SongCard artist={currentRound?.revealedArtist} title={currentRound?.revealedTitle} year={currentRound?.revealedYear} color={currentRound?.revealedColor} className={`gameplay-reveal-flip shadow-[6px_6px_0_var(--shadow-color)] ${currentRound?.placementCorrect ? "" : "opacity-70"}`} />
      <span className={`absolute -right-2 -top-2 flex size-8 items-center justify-center rounded-full border-[3px] border-background ${currentRound?.placementCorrect ? "bg-green" : "bg-destructive"}`}>{currentRound?.placementCorrect ? <Check className="size-4 text-[#11111b]" strokeWidth={3} /> : <X className="size-4 text-[#11111b]" strokeWidth={3} />}</span>
    </div>);
  }
  if (isLocked) {
    bets.forEach((bet) => {
      if (bet.position === undefined) return;
      const bettor = players.find((player) => player.id === bet.playerId);
      slots.set(bet.position, <BetCoin bettorName={bettor?.displayName} colorIndex={colorIndexOf(bettor)} />);
    });
  }
  const stakeGap = betDrag ? betDrag.hoverGap : stagedBetGap;
  if (canStakeBet && stakeGap !== null) slots.set(stakeGap, <DashedGap tone="green" isSmall>{betDrag ? null : <Coin size="small" className="gameplay-wobble" />}</DashedGap>);

  const tiltGapIndex = isAwaitingPlacement ? (isActivePlayer ? placementDrag?.hoverGap ?? null : spectatorPreviewGap) : null;
  const canCurrentViewerBet = canStakeBet || Boolean(myBet);
  const isTimelineDimmed = (isAwaitingPlacement && !isActivePlayer) || (isLocked && !isRevealed && !canCurrentViewerBet);
  const focusKey = isRevealed ? `revealed-${currentRound?.id}` : isLocked ? `locked-${currentRound?.id}` : droppedGap !== null && !placementDrag ? `dropped-${droppedGap}` : null;

  // --- Header status line ----------------------------------------------------------
  let statusDetail: ReactNode = `First to ${winConditionCardCount} cards wins`;
  let statusLabel: ReactNode = null;
  let statusClassName = "text-primary";
  if (isAwaitingPlacement) {
    if (isActivePlayer && placementDrag) statusLabel = "Placing your card...";
    else if (isActivePlayer && droppedGap !== null) { statusDetail = "You can still drag it to a different spot"; statusLabel = "Confirm your placement"; }
    else if (isActivePlayer) statusLabel = "Your turn";
    else if (isDj) { statusDetail = `${activePlayer?.displayName ?? "The active player"} is placing their card once the song starts`; statusLabel = "You're the DJ"; statusClassName = "text-accent"; }
    else { statusDetail = <><b className="text-foreground">{activePlayer?.displayName ?? "A player"}</b> is placing their card</>; statusLabel = <span className="tracking-[3px]">•••</span>; statusClassName = "text-muted-foreground"; }
  } else if (roundStatus === COUNTDOWN_STATUS) {
    if (isActivePlayer) { statusDetail = null; statusLabel = <span className="flex items-center gap-2 font-sans text-xs font-semibold text-green">Card locked in<StatusDot /><span className="flex items-center gap-1 text-muted-foreground"><VolumeX className="size-3.5" />Audio stopped</span></span>; }
    else if (canStakeBet) { statusDetail = "Get in position, you can't drop it until betting opens"; statusLabel = "Betting opens soon"; }
    else statusLabel = "Betting opens soon";
  } else if (roundStatus === BETTING_STATUS) {
    statusClassName = "text-destructive";
    statusLabel = "Betting window";
    if (isActivePlayer) statusDetail = "You can't bet on your own card";
    else if (myBet) statusDetail = "You staked your token";
    else if (canStakeBet) statusDetail = "Drag a token into a gap you think is right";
    else if (isDj) statusDetail = "The DJ sits out the betting";
    else statusDetail = "No tokens to bet this round";
  } else if (isRevealed) {
    statusDetail = null;
    statusClassName = currentRound?.placementCorrect ? "text-green" : "text-destructive";
    statusLabel = <span className="flex items-center gap-1.5 font-sans text-xs font-semibold">{currentRound?.placementCorrect ? <Check className="size-3.5" strokeWidth={3} /> : <X className="size-3.5" strokeWidth={3} />}{currentRound?.placementCorrect ? "Correct! Card locked in" : "Wrong spot"}</span>;
  }

  // --- Caption under the timeline -------------------------------------------------
  let caption: ReactNode = null;
  if (isAwaitingPlacement && isActivePlayer && placementDrag?.hoverGap !== null && placementDrag?.hoverGap !== undefined) caption = <GapCaption verb="Dropping" cards={cards} gapIndex={placementDrag.hoverGap} />;
  else if (isAwaitingPlacement && isActivePlayer && droppedGap !== null && !placementDrag) caption = <GapCaption verb="Placed" cards={cards} gapIndex={droppedGap} />;
  else if (isAwaitingPlacement && !isActivePlayer && !isDj) caption = "Watching live. It's not your turn to place a card.";
  else if (canStakeBet && stakeGap !== null) caption = <GapCaption verb={roundStatus === BETTING_STATUS ? "Staking" : "Lining up"} cards={cards} gapIndex={stakeGap} />;
  else if (isLocked && !isRevealed && myBet?.position !== undefined) caption = <GapCaption verb="You staked" cards={cards} gapIndex={myBet.position} />;
  else if (isLocked && !isRevealed && bets.length > 0) caption = `${bets.length === 1 ? "One token is" : `${bets.length} tokens are`} staked on this card.`;
  else if (isRevealed) {
    const isWinnerTimelineScored = roundStatus === SCORED_STATUS;
    const winnerCardCount = (cardWinner?.timeline?.length ?? 0) + (isWinnerTimelineScored ? 0 : 1);
    caption = cardWinner
      ? <><b className="text-foreground">{cardWinner.displayName}</b> {cardWinner.id === activePlayer?.id ? "takes" : "steals"} the card. {winnerCardCount} of {winConditionCardCount} to win.</>
      : "Nobody called it. The card is discarded.";
  }

  // --- Element floating above the timeline ------------------------------------------
  let aboveTimeline: ReactNode = null;
  if (isAwaitingPlacement && isActivePlayer && droppedGap === null && !placementDrag) {
    aboveTimeline = <div className="flex flex-col items-center gap-2.5">
      <span className="text-[11px] font-bold uppercase tracking-[2.5px] text-primary">Your card</span>
      <div className="gameplay-card-float"><MysteryCard size="small" isInteractive levels={audioLevels} label="Your card. Choose a timeline position." onPointerDown={startPlacementDrag} onKeyDown={moveDroppedCard} /></div>
      <span className="flex items-center gap-1.5 text-[11px] text-muted-foreground"><ArrowDown className="size-3" strokeWidth={2.5} />Drag onto the timeline</span>
    </div>;
  } else if (isAwaitingPlacement && isDj) {
    const linkOut = linkOutQuery.data;
    aboveTimeline = <div className="flex w-max max-w-[calc(100vw-48px)] flex-col items-center gap-6 sm:flex-row sm:items-center sm:gap-8">
      <SongCard artist={linkOut?.artist} title={linkOut?.title} year={linkOut?.releaseYear} color={linkOut?.color} className="shadow-[6px_6px_0_var(--shadow-color)]" />
      <div className="flex max-w-[360px] flex-col items-center gap-3 text-center sm:items-start sm:text-left">
        {linkOut?.watchUrl
          ? <a href={youtubeLinkOutHref(linkOut.watchUrl, navigator.userAgent)} target={YOUTUBE_LINK_OUT_TARGET} rel={YOUTUBE_LINK_OUT_REL} onClick={() => setIsLinkOutOpen(true)} className="inline-flex items-center gap-2.5 rounded-full bg-primary px-6 py-3 font-display text-[13px] text-primary-foreground shadow-[3px_3px_0_var(--shadow-color)] transition-transform hover:-translate-y-0.5">Open on YouTube to play<ExternalLink className="size-4" /></a>
          : <span className="inline-flex items-center gap-2.5 rounded-full bg-primary px-6 py-3 font-display text-[13px] text-primary-foreground opacity-55">Open on YouTube to play<ExternalLink className="size-4" /></span>}
        <p className="text-xs leading-relaxed text-muted-foreground">Play it out loud. Everyone else finds out what it is when the card gets revealed.</p>
        {isLinkOutOpen
          ? <button type="button" onClick={requestDjTabAudioShare} disabled={isTabAudioShared} className="inline-flex items-center gap-2 rounded-full border-2 border-warning bg-warning/10 px-3.5 py-1.5 text-[11px] font-bold text-warning transition-colors enabled:hover:bg-warning/20 disabled:opacity-80"><Volume2 className="size-3.5" />{isTabAudioShared ? "Sharing YouTube audio with the group" : "Share YouTube audio with the group"}</button>
          : <div className="inline-flex items-center gap-2 rounded-full border-2 border-warning bg-warning/10 px-3.5 py-1.5 text-[11px] font-bold text-warning"><TriangleAlert className="size-3.5" />Shares your tab or system audio with the group.</div>}
      </div>
    </div>;
  } else if (roundStatus === COUNTDOWN_STATUS) {
    const secondsLeft = secondsUntil(currentRound?.countdownEndsAt, now);
    aboveTimeline = <div className="relative size-14" aria-label={`Betting opens in ${secondsLeft}`}>
      <svg viewBox={`0 0 ${COUNTDOWN_RING_SIZE} ${COUNTDOWN_RING_SIZE}`} className="size-full -rotate-90" aria-hidden="true"><circle cx={COUNTDOWN_RING_CENTER} cy={COUNTDOWN_RING_CENTER} r={COUNTDOWN_RING_RADIUS} fill="none" strokeWidth="5" className="stroke-card" /><circle cx={COUNTDOWN_RING_CENTER} cy={COUNTDOWN_RING_CENTER} r={COUNTDOWN_RING_RADIUS} fill="none" strokeWidth="5" strokeLinecap="round" className="stroke-primary transition-[stroke-dashoffset] duration-200" strokeDasharray={COUNTDOWN_RING_CIRCUMFERENCE} strokeDashoffset={COUNTDOWN_RING_CIRCUMFERENCE * (1 - fractionRemaining(currentRound?.countdownEndsAt, LOCK_IN_COUNTDOWN_MILLISECONDS, now))} /></svg>
      <span className="absolute inset-0 flex items-center justify-center font-display text-lg text-foreground">{Math.max(secondsLeft, 1)}</span>
    </div>;
  } else if (roundStatus === BETTING_STATUS) {
    aboveTimeline = <div className="flex flex-col items-center gap-1.5">
      <span className="gameplay-timer-blink font-display text-[28px] text-destructive">{formatClock(secondsUntil(currentRound?.bettingWindowEndsAt, now))}</span>
      <span className="text-[11px] font-bold uppercase tracking-[2px] text-muted-foreground">Betting closes</span>
      <button type="button" onClick={skipBetting} className="mt-1 rounded-full border-2 border-border bg-card px-3.5 py-1 text-[11px] font-semibold text-card-foreground transition-colors hover:border-primary">Skip betting</button>
    </div>;
  } else if (isRevealed && currentRound?.nextRoundStartsAt) {
    aboveTimeline = <div className="flex flex-col items-center gap-3.5">
      <span className="text-xs font-bold uppercase tracking-[2px] text-muted-foreground">Next turn starts in</span>
      <div className="flex items-center gap-7 rounded-full border-2 border-border bg-card px-7 py-3.5 shadow-[4px_4px_0_var(--shadow-color)]">
        <RolePill label="DJ" name={nextTurn.nextDj?.displayName} colorIndex={colorIndexOf(nextTurn.nextDj)} size="large" />
        <span className="h-[30px] w-px bg-border" />
        <RolePill label="Turn" name={nextTurn.nextActive?.displayName} colorIndex={colorIndexOf(nextTurn.nextActive)} size="large" />
      </div>
      <div className="h-1.5 w-[220px] overflow-hidden rounded-full bg-border"><div className="h-full rounded-full bg-primary transition-[width] duration-200 ease-linear" style={{ width: `${Math.round(fractionRemaining(currentRound.nextRoundStartsAt, REVEAL_HOLD_MILLISECONDS, now) * PERCENT)}%` }} /></div>
    </div>;
  }

  // --- Element below the timeline -----------------------------------------------------
  let belowTimeline: ReactNode = null;
  if (isAwaitingPlacement && isActivePlayer && droppedGap !== null && !placementDrag) {
    belowTimeline = <button type="button" onClick={lockIn} className="inline-flex items-center gap-2.5 rounded-full bg-primary px-6 py-3 font-display text-[13px] text-primary-foreground shadow-[3px_3px_0_var(--shadow-color)] transition-transform hover:-translate-y-0.5"><Lock className="size-4" strokeWidth={2.5} />Lock in answer</button>;
  } else if (isAwaitingPlacement && !isDj) {
    belowTimeline = <div className="flex w-full max-w-[560px] flex-col items-center gap-3">
      <div className="flex w-full flex-col gap-3 sm:flex-row sm:gap-5">
        <GuessPill icon={<UserRound className="size-4 shrink-0" />} placeholder="Guess the artist" value={artistGuess} onChange={setArtistGuess} onSubmit={submitGuess} />
        <GuessPill icon={<Music2 className="size-4 shrink-0" />} placeholder="Guess the title" value={titleGuess} onChange={setTitleGuess} onSubmit={submitGuess} />
      </div>
      <p className="text-center text-[11.5px] text-muted-foreground">{isActivePlayer ? "Anyone can guess, anytime. Songs can credit more than one artist, so try them one at a time." : "Anyone can guess, anytime, no matter whose turn it is."}</p>
    </div>;
  } else if (isRevealed) {
    belowTimeline = <p className="flex items-center gap-2 text-xs text-muted-foreground"><Lock className="size-3.5" />Round closed. Next card is dealt in a moment.</p>;
  } else if (isLocked && !isDj) {
    belowTimeline = <p className="flex items-center gap-2 text-xs text-muted-foreground"><Lock className="size-3.5" />Guessing is closed for this card</p>;
  }

  const isSittingOut = isActivePlayer && (roundStatus === COUNTDOWN_STATUS || roundStatus === BETTING_STATUS);

  return <main className="relative flex min-h-full flex-col overflow-hidden px-6 pb-8 pt-8 sm:px-14 sm:pb-10 sm:pt-9">
    {showsIntro ? <RoundIntro winConditionCardCount={winConditionCardCount} roundNumber={roundNumber} countdownSeconds={ROUND_INTRO_SECONDS} dj={{ name: djPlayer?.displayName, colorIndex: colorIndexOf(djPlayer) }} activePlayer={{ name: activePlayer?.displayName, colorIndex: colorIndexOf(activePlayer) }} onFinished={dismissIntro} /> : null}
    {turnNoticePlayer && !showsIntro ? <div role="status" className="absolute left-1/2 top-6 z-30 flex w-[min(520px,calc(100%-32px))] -translate-x-1/2 items-center justify-between gap-4 rounded-full border-[3px] border-primary bg-card px-7 py-4 shadow-[6px_6px_0_var(--shadow-color)] animate-in fade-in-0 slide-in-from-top-2">
      <span className="flex min-w-0 items-center gap-3"><Bell className="size-6 shrink-0 text-primary" /><span className="truncate text-base font-bold text-primary">{turnNoticePlayer.id === currentPlayer?.id ? "Your turn: get ready" : `${turnNoticePlayer.displayName ?? "A player"}'s turn: get ready`}</span></span>
      <span className="shrink-0 text-xs text-muted-foreground">Round {roundNumber}</span>
    </div> : null}

    <header className="flex shrink-0 items-start justify-between gap-4">
      <div className="min-w-0">
        <h1 className="font-display text-[26px] text-foreground [text-shadow:3px_3px_0_var(--text-shadow-on-page)] sm:text-[32px]">Round {roundNumber}</h1>
        <div className="mt-2 flex flex-wrap items-center gap-2.5 text-xs text-muted-foreground">
          {statusDetail ? <span>{statusDetail}</span> : null}
          {statusDetail && statusLabel ? <StatusDot /> : null}
          {statusLabel ? <strong className={`font-display text-xs font-normal ${statusClassName}`}>{statusLabel}</strong> : null}
          <span className={`size-2 rounded-full ${realtime.connectionState === "connected" ? "bg-green" : "bg-muted-foreground"}`} role="img" aria-label={`Session connection ${realtime.connectionState}`} />
        </div>
      </div>
      <div className="hidden shrink-0 items-center gap-2.5 rounded-full border-2 border-border bg-card py-2 pl-2 pr-4 sm:inline-flex">
        <span className="size-[26px] rounded-lg" style={{ background: playlistColor ? (playlistColor.startsWith("#") ? playlistColor : `#${playlistColor}`) : "var(--accent)" }} />
        <span className="max-w-[200px] truncate text-[13px] font-semibold text-card-foreground">{playlistName}</span>
        {songCount > 0 ? <><StatusDot /><span className="text-xs text-muted-foreground">{songCount} songs</span></> : null}
      </div>
    </header>

    <section className="relative min-h-[560px] flex-1" aria-label="Game stage">
      <div className="absolute left-1/2 top-1/2 flex w-full max-w-[1100px] -translate-x-1/2 -translate-y-1/2 justify-center">
        {currentRound ? <GameplayTimeline cards={cards} slots={slots} tiltGapIndex={tiltGapIndex} focusKey={focusKey} isDimmed={isTimelineDimmed} trackReference={trackReference} /> : <p className="py-12 text-sm text-muted-foreground">Your timeline will appear when the round starts.</p>}
      </div>
      {currentRound ? <p className="absolute bottom-[calc(50%+98px)] left-1/2 -translate-x-1/2 text-xs font-bold uppercase tracking-[3px] text-muted-foreground">Timeline</p> : null}
      {aboveTimeline ? <div className="absolute bottom-[calc(50%+132px)] left-1/2 flex -translate-x-1/2 justify-center">{aboveTimeline}</div> : null}
      {caption ? <p className="absolute left-1/2 top-[calc(50%+98px)] w-max max-w-[calc(100%-16px)] -translate-x-1/2 text-center text-xs text-muted-foreground">{caption}</p> : null}
      {belowTimeline ? <div className="absolute left-1/2 top-[calc(50%+148px)] flex w-full -translate-x-1/2 justify-center px-3">{belowTimeline}</div> : null}
      <p className="absolute bottom-0 left-1/2 w-max max-w-full -translate-x-1/2 text-center text-xs text-muted-foreground" aria-live="polite">{feedbackMessage}</p>
    </section>

    {isChatOpen ? <GroupChatOverlay groupId={session.groupId ?? 0} connectionState={groupRealtime.connectionState} sendChat={groupRealtime.sendChat} onClose={() => setIsChatOpen(false)} /> : null}

    <footer className="flex shrink-0 flex-wrap items-end justify-between gap-4">
      <div className="flex items-center gap-3">
        <div className="inline-flex items-center gap-4 rounded-full border-2 border-border bg-card py-2.5 pl-2.5 pr-5">
          <RolePill label="DJ" name={djPlayer?.displayName} colorIndex={colorIndexOf(djPlayer)} />
          <span className="h-7 w-px bg-border" />
          <RolePill label="Turn" name={activePlayer?.displayName} colorIndex={colorIndexOf(activePlayer)} />
        </div>
        <button type="button" onClick={() => setIsChatOpen((isOpen) => !isOpen)} aria-label={isChatOpen ? "Close chat" : "Open chat"} className={`relative z-20 flex size-11 items-center justify-center rounded-full border-2 bg-card text-card-foreground transition-colors ${isChatOpen ? "border-primary text-primary" : "border-border hover:border-primary"}`}><MessageCircle className="size-[18px]" /></button>
      </div>
      <div onPointerDown={startBetDrag} className={canStakeBet ? "cursor-grab touch-none select-none active:cursor-grabbing" : ""} title={canStakeBet ? "Drag a token onto the timeline to bet" : undefined}>
        <TokenPile tokenCount={Math.max(0, tokenCount - (betDrag ? 1 : 0))} label={isSittingOut ? "Sitting out" : "Your tokens"} isDimmed={isSittingOut} />
      </div>
    </footer>

    {placementDrag ? <div className="pointer-events-none fixed left-0 top-0 z-50" style={{ transform: `translate(${placementDrag.pointerX - DRAGGED_CARD_HALF_SIZE_PIXELS}px, ${placementDrag.pointerY - DRAGGED_CARD_HALF_SIZE_PIXELS - DRAGGED_CARD_LIFT_PIXELS}px)` }}>
      <div className="gameplay-wobble"><MysteryCard levels={audioLevels} className="shadow-[10px_10px_0_var(--shadow-color)]" /></div>
    </div> : null}
    {betDrag ? <div className="pointer-events-none fixed left-0 top-0 z-50" style={{ transform: `translate(${betDrag.pointerX - DRAGGED_COIN_HALF_SIZE_PIXELS}px, ${betDrag.pointerY - DRAGGED_COIN_HALF_SIZE_PIXELS}px)` }}>
      <Coin size="large" className="gameplay-wobble" />
    </div> : null}
  </main>;
}

function GuessPill({ icon, placeholder, value, onChange, onSubmit }: { icon: ReactNode; placeholder: string; value: string; onChange: (value: string) => void; onSubmit: () => void }) {
  return <label className="flex h-[52px] w-full min-w-0 items-center gap-2.5 rounded-full border-2 border-border bg-card pl-4 pr-1.5 text-muted-foreground shadow-[3px_3px_0_var(--shadow-color)] sm:w-[260px]">
    {icon}
    <input value={value} onChange={(event) => onChange(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") onSubmit(); }} placeholder={placeholder} className="min-w-0 flex-1 bg-transparent text-sm text-card-foreground outline-none placeholder:text-muted-foreground" />
    <button type="button" onClick={onSubmit} disabled={!value.trim()} aria-label={`Submit ${placeholder.toLowerCase()}`} className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground transition-transform enabled:hover:scale-105 disabled:opacity-45"><ArrowRight className="size-4" strokeWidth={2.5} /></button>
  </label>;
}
