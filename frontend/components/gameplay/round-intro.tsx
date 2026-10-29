"use client";

import { useCallback, useEffect, useState } from "react";

import { PlayerAvatar } from "./game-pieces";
import { serverNow } from "@/lib/server-clock";

const RING_RADIUS = 60;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;
const RING_VIEWBOX_SIZE = 140;
const RING_CENTER = RING_VIEWBOX_SIZE / 2;
const TICK_MILLISECONDS = 1_000;
const MILLISECONDS_PER_SECOND = 1_000;

interface RoundIntroProps {
  winConditionCardCount: number;
  roundNumber: number;
  countdownSeconds: number;
  endsAt?: string;
  dj?: { name?: string; colorIndex: number };
  activePlayer?: { name?: string; colorIndex: number };
  onFinished: () => void;
}

// The first-round opener: who DJs and who plays first, over a ring that drains at a
// steady pace before the game screen takes over.
export function RoundIntro({ winConditionCardCount, roundNumber, countdownSeconds, endsAt, dj, activePlayer, onFinished }: RoundIntroProps) {
  const remainingSeconds = useCallback(() => endsAt
    ? Math.max(0, Math.ceil((Date.parse(endsAt) - serverNow()) / MILLISECONDS_PER_SECOND))
    : countdownSeconds, [countdownSeconds, endsAt]);
  const [secondsLeft, setSecondsLeft] = useState(remainingSeconds);

  useEffect(() => {
    if (secondsLeft <= 0) {
      onFinished();
      return;
    }
    const timeout = window.setTimeout(() => setSecondsLeft(remainingSeconds()), TICK_MILLISECONDS);
    return () => window.clearTimeout(timeout);
  }, [onFinished, remainingSeconds, secondsLeft]);

  return (
    <div role="status" aria-live="polite" className="absolute inset-0 z-40 flex flex-col items-center justify-center overflow-hidden bg-background bg-dotted px-6 text-center">
      <div className="flex flex-col items-center">
        <p className="mb-[18px] text-[13px] font-bold uppercase tracking-[4px] text-muted-foreground">Target: {winConditionCardCount} cards. Everyone finishes the round.</p>
        <h2 className="mb-[44px] font-display text-[72px] text-foreground [text-shadow:5px_5px_0_rgba(0,0,0,0.4)]">Round {roundNumber}</h2>
      </div>
      <div className="relative mb-[48px] size-[140px]">
        <svg viewBox={`0 0 ${RING_VIEWBOX_SIZE} ${RING_VIEWBOX_SIZE}`} className="size-full -rotate-90" aria-hidden="true">
          <circle cx={RING_CENTER} cy={RING_CENTER} r={RING_RADIUS} fill="none" strokeWidth="10" className="stroke-card" />
          <circle cx={RING_CENTER} cy={RING_CENTER} r={RING_RADIUS} fill="none" strokeWidth="10" strokeLinecap="round" strokeDasharray={RING_CIRCUMFERENCE} className="stroke-accent" style={{ ["--ring-circumference" as string]: `${RING_CIRCUMFERENCE}`, animation: `gameplay-ring-drain ${countdownSeconds}s linear forwards` }} />
        </svg>
        <span className="absolute inset-0 flex items-center justify-center font-display text-[48px] text-foreground">{Math.max(secondsLeft, 1)}</span>
      </div>
      <div className="flex items-center gap-[56px]">
        <RoleAnnouncement label="First DJ" labelClassName="text-primary" ringClassName="shadow-[0_0_0_4px_var(--background),0_0_0_9px_var(--primary)]" player={dj} />
        <span className="h-[90px] w-[2px] bg-border" />
        <RoleAnnouncement label="First turn" labelClassName="text-accent" ringClassName="shadow-[0_0_0_4px_var(--background),0_0_0_9px_var(--accent)]" player={activePlayer} animationDelay="-1.5s" />
      </div>
    </div>
  );
}

function RoleAnnouncement({
  label,
  labelClassName,
  ringClassName,
  player,
  animationDelay,
}: {
  label: string;
  labelClassName: string;
  ringClassName: string;
  player?: { name?: string; colorIndex: number };
  animationDelay?: string;
}) {
  return (
    <div className="gameplay-role-float flex flex-col items-center gap-[22px]" style={animationDelay ? { animationDelay } : undefined}>
      <span className={`text-[12px] font-bold uppercase tracking-[3px] ${labelClassName}`}>{label}</span>
      <div className={`rounded-full ${ringClassName}`}>
        <PlayerAvatar name={player?.name} colorIndex={player?.colorIndex ?? 0} className="size-[88px] text-[28px]" />
      </div>
      <span className="font-display text-[20px] text-foreground">{player?.name ?? "Player"}</span>
    </div>
  );
}
