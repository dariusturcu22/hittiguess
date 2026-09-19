"use client";

import { use, useCallback, useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { Bell, ChevronLeft, ChevronRight, ExternalLink, Loader2, MessageCircle, Music2, Send, UserRound, UsersRound, Volume2 } from "lucide-react";

import { useGetCurrentRoundLinkOut, useGetSession } from "@/hooks/generated/game-session/game-session";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import type { PlayerCardDTO } from "@/hooks/models/playerCardDTO";
import { useGameSessionRealtime } from "@/hooks/use-game-session-realtime";
import { useGroupRealtime } from "@/hooks/use-group-realtime";
import { openYoutubeLink } from "@/lib/youtube-link-out";
import { GroupChatOverlay } from "@/components/group-chat-overlay";

const TIMELINE_CARD_COLORS = ["bg-teal", "bg-accent", "bg-blue", "bg-green", "bg-warning", "bg-pink", "bg-primary"];
const ROUND_NUMBER_FALLBACK = 1;
const TIMELINE_MASK = "linear-gradient(90deg,transparent,#000 7%,#000 93%,transparent)";
const SOUNDSWAVE_BAR_CLASSES = ["h-5", "h-9", "h-12", "h-7", "h-10"];
const AWAITING_PLACEMENT_STATUS = "AWAITING_PLACEMENT";
const BETTING_STATUS = "BETTING";
const COUNTDOWN_STATUS = "COUNTDOWN";
const COMPLETED_SESSION_STATUS = "COMPLETED";
const TURN_NOTICE_DURATION_MILLISECONDS = 5_000;
const TURN_NOTICE_START_DELAY_MILLISECONDS = 0;

interface PageProps { params: Promise<{ sessionId: string }>; }

function TimelineCard({ card, index }: { card: PlayerCardDTO; index: number }) {
  const colorClass = TIMELINE_CARD_COLORS[index % TIMELINE_CARD_COLORS.length];
  return <article className={`flex h-[132px] w-[132px] shrink-0 flex-col items-center justify-center rounded-[20px] border-[5px] border-background px-3 text-center text-primary-foreground shadow-[5px_5px_0_rgba(0,0,0,0.28)] sm:h-[168px] sm:w-[168px] ${colorClass}`}>
    <span className="line-clamp-2 text-xs font-bold uppercase">{card.title ?? "Unknown song"}</span>
    <strong className="my-1 font-display text-3xl sm:text-[40px]">{card.releaseYear ?? "?"}</strong>
    <span className="line-clamp-2 text-[10px] font-semibold opacity-80">{card.title ?? "Keep listening"}</span>
  </article>;
}

function SoundwaveCard({ draggable = false, onDragEnd, onDragStart, onKeyDown }: { draggable?: boolean; onDragEnd?: () => void; onDragStart?: () => void; onKeyDown?: (event: KeyboardEvent<HTMLButtonElement>) => void }) {
  return <button type="button" draggable={draggable} onDragEnd={onDragEnd} onDragStart={onDragStart} onKeyDown={onKeyDown} className={`flex size-32 items-center justify-center rounded-[20px] border-[5px] border-border bg-card shadow-[5px_5px_0_rgba(0,0,0,0.3)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${draggable ? "cursor-grab active:cursor-grabbing" : ""}`} aria-label={draggable ? "Your card. Choose a timeline position." : "Song card"}><div className="flex h-12 items-center gap-1.5">{SOUNDSWAVE_BAR_CLASSES.map((heightClass) => <span key={heightClass} className={`w-[7px] rounded-full bg-green ${heightClass}`} />)}</div></button>;
}

function DropTarget({ isVisible, position, onPlace }: { isVisible: boolean; position: number; onPlace: (position: number) => void }) {
  if (!isVisible) return null;
  return <button type="button" onDragOver={(event) => event.preventDefault()} onDrop={() => onPlace(position)} className="flex h-[132px] w-9 shrink-0 items-center justify-center rounded-full border-2 border-dashed border-primary/70 bg-primary/10 text-[9px] font-bold uppercase tracking-[0.08em] text-primary transition-colors hover:bg-primary/20 sm:h-[168px]" aria-label={`Place card at timeline position ${position + 1}`}>Drop</button>;
}

function GuessField({ placeholder, value, onChange, onSubmit }: { placeholder: string; value: string; onChange: (value: string) => void; onSubmit: () => void }) {
  return <label className="flex h-12 w-full min-w-0 items-center gap-2 rounded-full border-2 border-border bg-card px-4 text-muted-foreground sm:w-[242px]"><Music2 className="size-4 shrink-0" /><input value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} className="min-w-0 flex-1 bg-transparent text-sm text-card-foreground outline-none placeholder:text-muted-foreground" /><button type="button" onClick={onSubmit} disabled={!value.trim()} aria-label={`Submit ${placeholder.toLowerCase()}`} className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground transition-transform enabled:hover:scale-105 disabled:opacity-45"><Send className="size-3.5" /></button></label>;
}

export default function GameSessionPage({ params }: PageProps) {
  const { sessionId: sessionIdParam } = use(params);
  const sessionId = Number(sessionIdParam);
  const router = useRouter();
  const sessionQuery = useGetSession(sessionId, { query: { retry: false } });
  const currentUserQuery = useGetCurrentUser();
  const session = sessionQuery.data;
  const groupRealtime = useGroupRealtime(session?.groupId ?? 0);
  const currentRound = session?.currentRound;
  const currentPlayer = session?.players?.find((player) => player.userId === currentUserQuery.data?.id);
  const isDj = currentPlayer?.id === currentRound?.djPlayerId;
  const linkOutQuery = useGetCurrentRoundLinkOut(sessionId, { query: { enabled: Boolean(isDj), retry: false } });
  const [isLinkOutOpen, setIsLinkOutOpen] = useState(false);
  const [artistGuess, setArtistGuess] = useState("");
  const [titleGuess, setTitleGuess] = useState("");
  const [isDraggingCard, setIsDraggingCard] = useState(false);
  const [isSelectingBet, setIsSelectingBet] = useState(false);
  const [isChatOpen, setIsChatOpen] = useState(false);
  const [feedbackMessage, setFeedbackMessage] = useState("");
  const [showTurnNotice, setShowTurnNotice] = useState(false);
  const previousActivePlayerId = useRef<number | undefined>(undefined);
  const handleRoundEvent = useCallback((event: { type: string }) => {
    if (event.type !== "ROUND_STARTED" && event.type !== "NEXT_ROUND") {
      return;
    }
    setShowTurnNotice(true);
    window.setTimeout(() => setShowTurnNotice(false), TURN_NOTICE_DURATION_MILLISECONDS);
  }, []);
  const realtime = useGameSessionRealtime(sessionId, handleRoundEvent);

  useEffect(() => {
    if (session?.status === COMPLETED_SESSION_STATUS) router.replace(`/sessions/${sessionId}/results`);
  }, [router, session?.status, sessionId]);

  const activePlayerId = session?.players?.find((player) => player.id === currentRound?.activePlayerId)?.id;
  useEffect(() => {
    if (previousActivePlayerId.current !== undefined && previousActivePlayerId.current !== activePlayerId) {
      const startTimeout = window.setTimeout(() => setShowTurnNotice(true), TURN_NOTICE_START_DELAY_MILLISECONDS);
      const timeout = window.setTimeout(() => setShowTurnNotice(false), TURN_NOTICE_DURATION_MILLISECONDS);
      previousActivePlayerId.current = activePlayerId;
      return () => { window.clearTimeout(startTimeout); window.clearTimeout(timeout); };
    }
    previousActivePlayerId.current = activePlayerId;
  }, [activePlayerId, currentPlayer?.id]);

  if (!Number.isInteger(sessionId) || sessionId <= 0) return <main className="p-10 text-destructive">This game session link is invalid.</main>;
  if (sessionQuery.isLoading) return <main className="flex h-full min-h-[720px] items-center justify-center"><Loader2 className="size-8 animate-spin text-primary" aria-label="Loading game session" /></main>;
  if (sessionQuery.isError || !session) return <main className="p-10 text-destructive">This game session is unavailable.</main>;

  const activePlayer = session.players?.find((player) => player.id === currentRound?.activePlayerId);
  const djPlayer = session.players?.find((player) => player.id === currentRound?.djPlayerId);
  const timeline = activePlayer?.timeline ?? [];
  const roundNumber = currentRound?.roundNumber ?? session.currentRoundNumber ?? ROUND_NUMBER_FALLBACK;
  const isActivePlayer = currentPlayer?.id === activePlayer?.id;
  const canPlaceCard = isActivePlayer && currentRound?.status === AWAITING_PLACEMENT_STATUS;
  const canBet = currentRound?.status === BETTING_STATUS && !isActivePlayer && !isDj && (currentPlayer?.tokenCount ?? 0) > 0;
  const isBettingPrep = currentRound?.status === COUNTDOWN_STATUS;
  const isSpectator = Boolean(currentRound && !isActivePlayer && !isDj);
  const roundStatusLabel = currentRound?.status === COUNTDOWN_STATUS
    ? "BETTING OPENS SOON"
    : currentRound?.status?.replaceAll("_", " ") ?? "WAITING FOR PLAYERS";

  function openLinkOut() {
    const watchUrl = linkOutQuery.data?.watchUrl;
    if (!watchUrl) return;
    setIsLinkOutOpen(true);
    openYoutubeLink(watchUrl);
    window.dispatchEvent(new CustomEvent("session-start-audio-share"));
  }

  function submitGuess() {
    if (realtime.submitGuess(artistGuess, titleGuess)) {
      setArtistGuess("");
      setTitleGuess("");
      setFeedbackMessage("Guess sent. Keep listening for the result.");
    } else {
      setFeedbackMessage("The game connection is not ready. Try again in a moment.");
    }
  }

  function placeCard(position: number) {
    if (realtime.placeCard(position)) {
      setIsDraggingCard(false);
      setFeedbackMessage("Card placed. Waiting for the reveal.");
    } else {
      setFeedbackMessage("The game connection is not ready. Try again in a moment.");
    }
  }

  function selectTimelinePosition(position: number) {
    if (isSelectingBet) {
      if (realtime.placeBet(position)) {
        setIsSelectingBet(false);
        setFeedbackMessage("Bet placed. Waiting for the reveal.");
      } else {
        setFeedbackMessage("The game connection is not ready. Try again in a moment.");
      }
      return;
    }
    placeCard(position);
  }

  return <main className="relative flex min-h-[720px] flex-col overflow-hidden px-6 py-8 sm:px-14 sm:py-9">
    {showTurnNotice ? <div role="status" className="absolute left-1/2 top-7 z-30 flex w-[min(680px,calc(100%-32px))] items-center justify-between gap-4 rounded-full border-[3px] border-primary bg-primary/15 px-6 py-4 shadow-[6px_6px_0_rgba(0,0,0,0.3)] sm:px-8"><span className="flex min-w-0 items-center gap-3"><Bell className="size-6 shrink-0 text-primary" /><span className="truncate font-bold text-base text-primary">{activePlayer?.displayName ?? "A player"}&apos;s turn: get ready</span></span><span className="shrink-0 text-xs text-muted-foreground">Round {roundNumber}</span></div> : null}
    <header className="flex items-start justify-between gap-4"><div><h1 className="font-display text-[26px] text-foreground drop-shadow-sm sm:text-[32px]">Round {roundNumber}</h1><div className="mt-2 flex items-center gap-2 text-xs text-muted-foreground"><span>{isBettingPrep ? "You can&apos;t bet on your own card" : `First to ${session.winConditionCardCount ?? 0} cards wins`}</span><span className="size-1 rounded-full bg-muted-foreground" /><strong className="font-display text-accent">{roundStatusLabel}</strong></div></div><div className="inline-flex items-center gap-2 rounded-full border-2 border-border bg-card px-4 py-2 text-sm font-semibold text-card-foreground"><UsersRound className="size-4 text-accent" />{session.djMode === "FIXED" ? "Fixed DJ" : "Rotating DJ"}<span className={`size-2 rounded-full ${realtime.connectionState === "connected" ? "bg-green" : "bg-muted-foreground"}`} aria-label={`Session connection ${realtime.connectionState}`} /></div></header>
    <section className="relative flex min-h-[530px] flex-1 items-center justify-center"><p className="absolute bottom-[calc(50%+108px)] font-bold text-xs uppercase tracking-[0.24em] text-muted-foreground">Timeline</p><div className={`w-full max-w-[1100px] overflow-x-auto px-10 py-4 transition-opacity ${isBettingPrep ? "opacity-70 saturate-50" : ""}`} style={{ maskImage: TIMELINE_MASK }}><div className="flex w-max items-center gap-[18px]"><DropTarget isVisible={(isDraggingCard && canPlaceCard) || isSelectingBet} position={0} onPlace={selectTimelinePosition} />{timeline.map((card, index) => <div key={`${card.songId}-${card.position}`} className="flex items-center gap-[18px]"><TimelineCard card={card} index={index} /><DropTarget isVisible={(isDraggingCard && canPlaceCard) || isSelectingBet} position={index + 1} onPlace={selectTimelinePosition} /></div>)}{timeline.length === 0 ? <p className="py-12 text-sm text-muted-foreground">Your timeline will appear when the round starts.</p> : null}</div></div><button type="button" aria-label="View earlier timeline cards" className="absolute left-0 top-1/2 flex size-10 -translate-y-1/2 items-center justify-center rounded-full border-2 border-border bg-card shadow-[3px_3px_0_rgba(0,0,0,0.25)]"><ChevronLeft className="size-5" /></button><button type="button" aria-label="View later timeline cards" className="absolute right-0 top-1/2 flex size-10 -translate-y-1/2 items-center justify-center rounded-full border-2 border-border bg-card shadow-[3px_3px_0_rgba(0,0,0,0.25)]"><ChevronRight className="size-5" /></button>
      {currentRound && isDj ? <div className="absolute bottom-[calc(50%+178px)] left-1/2 flex w-max max-w-[calc(100%-24px)] -translate-x-1/2 flex-col items-center gap-5 sm:flex-row sm:items-center sm:gap-7"><SoundwaveCard /><div className="max-w-[340px] text-center sm:text-left"><button type="button" onClick={openLinkOut} disabled={linkOutQuery.isLoading || !linkOutQuery.data?.watchUrl} className="inline-flex items-center gap-2 rounded-full bg-primary px-5 py-3 font-display text-[13px] text-primary-foreground shadow-[3px_3px_0_rgba(0,0,0,0.28)] disabled:opacity-55">Open on YouTube to play<ExternalLink className="size-4" /></button><p className="mt-2 text-xs leading-relaxed text-muted-foreground">Play it out loud. Everyone else finds out what it is when the card gets revealed.</p>{isLinkOutOpen ? <div className="mt-2 inline-flex items-center gap-2 rounded-full border-2 border-warning bg-warning/10 px-3 py-1.5 text-[11px] font-semibold text-warning"><Volume2 className="size-3.5" />Shares your tab or system audio with the group.</div> : null}</div></div> : null}
      {currentRound && isActivePlayer && !isDj ? <div className="absolute bottom-[calc(50%+148px)] left-1/2 flex -translate-x-1/2 flex-col items-center gap-2"><span className="font-bold text-[11px] uppercase tracking-[0.2em] text-primary">Your card</span><SoundwaveCard draggable={canPlaceCard} onDragStart={() => setIsDraggingCard(true)} onDragEnd={() => setIsDraggingCard(false)} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); setIsDraggingCard(true); } }} /><span className="text-[11px] text-muted-foreground">{canPlaceCard ? "Drag onto the timeline or focus a drop target" : "Waiting for the next placement"}</span></div> : null}
      {canBet ? <div className="absolute bottom-[calc(50%+148px)] left-1/2 flex -translate-x-1/2 flex-col items-center gap-3"><span className="font-bold text-[11px] uppercase tracking-[0.2em] text-warning">Betting window</span><button type="button" onClick={() => setIsSelectingBet((currentValue) => !currentValue)} className="rounded-full border-2 border-warning bg-warning/10 px-5 py-3 font-display text-xs text-warning shadow-[3px_3px_0_rgba(0,0,0,0.22)]">{isSelectingBet ? "Choose a timeline gap" : "Use 1 token to bet"}</button><span className="text-[11px] text-muted-foreground">Pick where you think the locked card belongs.</span></div> : null}
      {isBettingPrep ? <div className="absolute bottom-[calc(50%+148px)] left-1/2 flex -translate-x-1/2 flex-col items-center gap-2"><span className="font-display text-6xl text-primary drop-shadow-sm motion-safe:animate-pulse">Ready</span><span className="text-[11px] font-bold uppercase tracking-[0.18em] text-muted-foreground">Betting opens soon</span></div> : null}
      {isSpectator && currentRound?.status === AWAITING_PLACEMENT_STATUS ? <p className="absolute top-[calc(50%+104px)] text-center text-xs text-muted-foreground">Watching live. It&apos;s not your turn to place a card.</p> : null}
      {currentRound?.status === "REVEALED" || currentRound?.status === "SCORED" ? <div className="absolute bottom-[calc(50%+148px)] left-1/2 flex -translate-x-1/2 flex-col items-center gap-2 text-center"><span className="font-display text-lg text-green">{currentRound.revealedTitle ?? "Card revealed"}</span><span className="text-sm text-muted-foreground">{currentRound.revealedArtist ?? ""}{currentRound.revealedYear ? ` · ${currentRound.revealedYear}` : ""}</span><span className="text-[11px] text-muted-foreground">Round closed. The next card is dealt in a moment.</span></div> : null}
      {currentRound && !isDj ? <div className="absolute top-[calc(50%+148px)] left-1/2 flex w-full max-w-[520px] -translate-x-1/2 flex-col items-center gap-3 px-3"><div className="flex w-full flex-col gap-3 sm:flex-row sm:gap-5"><GuessField placeholder="Guess the artist" value={artistGuess} onChange={setArtistGuess} onSubmit={submitGuess} /><GuessField placeholder="Guess the title" value={titleGuess} onChange={setTitleGuess} onSubmit={submitGuess} /></div><p className="text-center text-[11.5px] text-muted-foreground">Anyone can guess, anytime. Try artists one at a time.</p></div> : null}<p className="absolute bottom-4 left-1/2 -translate-x-1/2 text-center text-xs text-muted-foreground" aria-live="polite">{feedbackMessage}</p></section>
    {isChatOpen ? <GroupChatOverlay groupId={session.groupId ?? 0} connectionState={groupRealtime.connectionState} sendChat={groupRealtime.sendChat} onClose={() => setIsChatOpen(false)} /> : null}
    <footer className="flex flex-wrap items-center justify-between gap-4"><div className="inline-flex items-center gap-3 rounded-full border-2 border-border bg-card px-4 py-2"><UserRound className="size-4 text-primary" /><span className="text-xs text-muted-foreground">DJ</span><strong className="text-sm text-card-foreground">{djPlayer?.displayName ?? "Waiting"}</strong><span className="h-7 w-px bg-border" /><span className="text-xs text-muted-foreground">Turn</span><strong className="text-sm text-card-foreground">{activePlayer?.displayName ?? "Waiting"}</strong></div><div className="flex items-center gap-3"><button type="button" onClick={() => setIsChatOpen((currentValue) => !currentValue)} className={`inline-flex items-center gap-2 rounded-full border-2 bg-card px-4 py-2 text-xs font-semibold text-card-foreground ${isChatOpen ? "border-primary text-primary" : "border-border"}`} aria-label={isChatOpen ? "Close chat" : "Open chat"}><MessageCircle className="size-4" />Chat</button><div className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"><Music2 className="size-4 text-warning" />Your tokens {currentPlayer?.tokenCount ?? 0}</div></div></footer>
  </main>;
}
