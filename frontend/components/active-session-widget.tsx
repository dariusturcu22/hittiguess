"use client";

import Link from "next/link";
import { ArrowRight, Clock3, Coins, Radio } from "lucide-react";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { useGetActiveMembership } from "@/hooks/generated/group-management/group-management";
import { useGetActiveSessionForGroup, useGetSession } from "@/hooks/generated/game-session/game-session";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import { useGameSessionRealtime } from "@/hooks/use-game-session-realtime";
import { useTurnSound } from "@/hooks/use-turn-sound";
import { AWAITING_PLACEMENT_STATUS, BETTING_STATUS, COUNTDOWN_STATUS, REVEALED_STATUS, SCORED_STATUS, secondsUntil } from "@/lib/gameplay-round";
import type { RoundDTO } from "@/hooks/models/roundDTO";

const LOCKED_GROUP_STATUS = "LOCKED";
const CLOCK_INTERVAL_MILLISECONDS = 1_000;

export function phaseDeadline(round: RoundDTO | undefined): string | undefined {
  switch (round?.status) {
    case AWAITING_PLACEMENT_STATUS: return round.placementEndsAt;
    case COUNTDOWN_STATUS: return round.countdownEndsAt;
    case BETTING_STATUS: return round.bettingWindowEndsAt;
    case REVEALED_STATUS:
    case SCORED_STATUS: return round.nextRoundStartsAt;
    default: return undefined;
  }
}

function phaseDescription(round: RoundDTO | undefined, playerName: string): string {
  switch (round?.status) {
    case AWAITING_PLACEMENT_STATUS: return `${playerName} is placing a card`;
    case COUNTDOWN_STATUS: return `${playerName} locked their placement`;
    case BETTING_STATUS: return "Betting is open";
    case REVEALED_STATUS:
    case SCORED_STATUS: return "The song is revealed";
    default: return "A round is in progress";
  }
}

export function ActiveSessionWidget() {
  const pathname = usePathname();
  const [now, setNow] = useState(Date.now);
  const membershipQuery = useGetActiveMembership({ query: { retry: false } });
  const currentUserQuery = useGetCurrentUser();
  const groupId = membershipQuery.data?.id ?? 0;
  const sessionQuery = useGetActiveSessionForGroup(groupId, { query: { enabled: membershipQuery.data?.status === LOCKED_GROUP_STATUS, retry: false } });
  const sessionId = sessionQuery.data?.id;
  const sessionDetailsQuery = useGetSession(sessionId ?? 0, { query: { enabled: sessionId !== undefined, retry: false } });
  const currentRound = sessionDetailsQuery.data?.currentRound;
  const players = sessionDetailsQuery.data?.players;
  const activePlayer = players?.find((player) => player.id === currentRound?.activePlayerId);
  const ownPlayer = players?.find((player) => player.userId === currentUserQuery.data?.id);
  const isAway = sessionId !== undefined && !pathname.startsWith(`/sessions/${sessionId}`);
  useGameSessionRealtime(isAway ? sessionId ?? 0 : 0, () => setNow(Date.now()));
  const isOwnTurn = ownPlayer?.id !== undefined && ownPlayer.id === currentRound?.activePlayerId && currentRound?.status === AWAITING_PLACEMENT_STATUS;
  const turnKey = sessionId !== undefined && currentRound?.id !== undefined ? `${sessionId}:${currentRound.id}` : undefined;
  const notifiedTurnsReference = useRef(new Set<string>());
  const playTurnSound = useTurnSound();
  const deadline = phaseDeadline(currentRound);

  useEffect(() => {
    if (!isAway || !isOwnTurn || !turnKey || notifiedTurnsReference.current.has(turnKey)) return;
    notifiedTurnsReference.current.add(turnKey);
    playTurnSound();
  }, [isAway, isOwnTurn, playTurnSound, turnKey]);

  useEffect(() => {
    if (!isAway) return;
    const interval = window.setInterval(() => setNow(Date.now()), CLOCK_INTERVAL_MILLISECONDS);
    return () => window.clearInterval(interval);
  }, [isAway]);

  if (!isAway || !sessionId) return null;
  const sessionPath = `/sessions/${sessionId}`;
  return <>
    {isOwnTurn ? <div role="status" aria-live="polite"><Link href={sessionPath} aria-label="Your turn. Return to game" className="fixed left-1/2 top-5 z-50 flex -translate-x-1/2 items-center gap-4 rounded-full border-[3px] border-border bg-primary px-6 py-3 font-display text-primary-foreground shadow-[4px_4px_0_var(--shadow-color)]">Your turn! <span className="text-xs">Return to game</span><ArrowRight className="size-4" /></Link></div> : null}
    <Link href={sessionPath} aria-label="Return to game in progress" className="fixed bottom-4 right-4 z-40 w-[min(310px,calc(100vw-2rem))] rounded-[16px] border-[3px] border-border bg-card p-5 shadow-[6px_6px_0_rgba(0,0,0,0.3)] transition-transform hover:-translate-y-1 sm:bottom-10 sm:right-10">
      <span className="flex items-center gap-2"><Radio className="size-3 text-green" /><span className="font-bold text-[13px] text-card-foreground">Game in progress</span></span>
      <span className="mt-3 block text-[13px] text-muted-foreground">{phaseDescription(currentRound, activePlayer?.displayName ?? "A player")}</span>
      <span className="mt-2 flex items-center gap-2 text-[13px] font-display text-blue"><Clock3 className="size-[18px]" />Round {currentRound?.roundNumber ?? ""}</span>
      <span className="mt-2 flex items-center gap-2 text-xs text-card-foreground"><Coins className="size-4 text-warning" />Your tokens: {ownPlayer?.tokenCount ?? 0}</span>
      {deadline ? <span className="mt-2 block text-xs tabular-nums text-muted-foreground">Phase ends in {secondsUntil(deadline, now)}s</span> : null}
      <span className="mt-4 inline-flex items-center gap-2 rounded-full bg-primary px-4 py-2.5 font-display text-xs text-primary-foreground">Return to game <ArrowRight className="size-4" /></span>
    </Link>
  </>;
}
