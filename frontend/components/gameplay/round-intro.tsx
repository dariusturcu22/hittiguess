"use client";

import { useEffect, useState } from "react";

import { PlayerAvatar } from "./game-pieces";

const RING_RADIUS = 60;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;
const RING_VIEWBOX_SIZE = 130;
const RING_CENTER = RING_VIEWBOX_SIZE / 2;
const TICK_MILLISECONDS = 1_000;

interface RoundIntroProps {
  winConditionCardCount: number;
  roundNumber: number;
  countdownSeconds: number;
  dj?: { name?: string; colorIndex: number };
  activePlayer?: { name?: string; colorIndex: number };
  onFinished: () => void;
}

// The first-round opener: who DJs and who plays first, over a ring that drains at a
// steady pace before the game screen takes over.
export function RoundIntro({ winConditionCardCount, roundNumber, countdownSeconds, dj, activePlayer, onFinished }: RoundIntroProps) {
  const [secondsLeft, setSecondsLeft] = useState(countdownSeconds);

  useEffect(() => {
    if (secondsLeft <= 0) {
      onFinished();
      return;
    }
    const timeout = window.setTimeout(() => setSecondsLeft((seconds) => seconds - 1), TICK_MILLISECONDS);
    return () => window.clearTimeout(timeout);
  }, [onFinished, secondsLeft]);

  return <div role="status" aria-live="polite" className="absolute inset-0 z-40 flex flex-col items-center justify-center gap-8 bg-background bg-dotted px-6 text-center">
    <div className="flex flex-col items-center gap-3">
      <p className="text-xs font-bold uppercase tracking-[4px] text-muted-foreground">First to {winConditionCardCount} cards wins</p>
      <h2 className="font-display text-6xl text-foreground [text-shadow:4px_4px_0_var(--text-shadow-on-page)] sm:text-7xl">Round {roundNumber}</h2>
    </div>
    <div className="relative size-[130px]">
      <svg viewBox={`0 0 ${RING_VIEWBOX_SIZE} ${RING_VIEWBOX_SIZE}`} className="size-full -rotate-90" aria-hidden="true">
        <circle cx={RING_CENTER} cy={RING_CENTER} r={RING_RADIUS} fill="none" strokeWidth="8" className="stroke-card" />
        <circle cx={RING_CENTER} cy={RING_CENTER} r={RING_RADIUS} fill="none" strokeWidth="8" strokeLinecap="round" strokeDasharray={RING_CIRCUMFERENCE} className="stroke-primary" style={{ ["--ring-circumference" as string]: `${RING_CIRCUMFERENCE}`, animation: `gameplay-ring-drain ${countdownSeconds}s linear forwards` }} />
      </svg>
      <span className="absolute inset-0 flex items-center justify-center font-display text-5xl text-foreground">{Math.max(secondsLeft, 1)}</span>
    </div>
    <div className="flex items-center gap-12">
      <RoleAnnouncement label="First DJ" labelClassName="text-accent" ringClassName="ring-accent" player={dj} />
      <span className="h-[90px] w-px bg-border" />
      <RoleAnnouncement label="First turn" labelClassName="text-primary" ringClassName="ring-primary" player={activePlayer} />
    </div>
  </div>;
}

function RoleAnnouncement({ label, labelClassName, ringClassName, player }: { label: string; labelClassName: string; ringClassName: string; player?: { name?: string; colorIndex: number } }) {
  return <div className="gameplay-role-float flex flex-col items-center gap-3">
    <span className={`text-xs font-bold uppercase tracking-[3px] ${labelClassName}`}>{label}</span>
    <div className={`rounded-full ring-4 ring-offset-4 ring-offset-background ${ringClassName}`}>
      <PlayerAvatar name={player?.name} colorIndex={player?.colorIndex ?? 0} className="size-[88px] text-3xl" />
    </div>
    <span className="text-lg font-semibold text-foreground">{player?.name ?? "Player"}</span>
  </div>;
}
