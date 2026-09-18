"use client";

import { use } from "react";
import { ChevronLeft, ChevronRight, Loader2, Music2, UserRound } from "lucide-react";

import { useGetSession } from "@/hooks/generated/game-session/game-session";

const TIMELINE_CARD_COLORS = ["bg-teal", "bg-accent", "bg-blue", "bg-green", "bg-warning", "bg-pink", "bg-primary"];

interface PageProps {
  params: Promise<{ sessionId: string }>;
}

function TimelineCard({ title, releaseYear, index }: { title?: string; releaseYear?: number; index: number }) {
  const colorClass = TIMELINE_CARD_COLORS[index % TIMELINE_CARD_COLORS.length];
  return (
    <article className={`flex h-[132px] w-[132px] shrink-0 flex-col items-center justify-center rounded-[20px] border-[5px] border-background px-3 text-center text-primary-foreground sm:h-[168px] sm:w-[168px] ${colorClass}`}>
      <span className="line-clamp-2 text-xs font-bold uppercase">{title ?? "Unknown song"}</span>
      <strong className="mt-1 font-display text-3xl sm:text-[40px]">{releaseYear ?? "?"}</strong>
    </article>
  );
}

export default function GameSessionPage({ params }: PageProps) {
  const { sessionId: sessionIdParam } = use(params);
  const sessionId = Number(sessionIdParam);
  const sessionQuery = useGetSession(sessionId, { query: { retry: false } });

  if (!Number.isInteger(sessionId) || sessionId <= 0) {
    return <main className="p-10 text-destructive">This game session link is invalid.</main>;
  }

  if (sessionQuery.isLoading) {
    return <main className="flex h-full items-center justify-center"><Loader2 className="size-8 animate-spin text-primary" /></main>;
  }

  if (sessionQuery.isError || !sessionQuery.data) {
    return <main className="p-10 text-destructive">This game session is unavailable.</main>;
  }

  const session = sessionQuery.data;
  const currentRound = session.currentRound;
  const activePlayer = session.players?.find((player) => player.id === currentRound?.activePlayerId);
  const djPlayer = session.players?.find((player) => player.id === currentRound?.djPlayerId);
  const timeline = activePlayer?.timeline ?? [];

  return (
    <main className="relative flex h-full min-h-[720px] flex-col overflow-hidden px-6 py-8 sm:px-14 sm:py-9">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-display text-[26px] text-foreground drop-shadow-sm sm:text-[32px]">Round {currentRound?.roundNumber ?? session.currentRoundNumber ?? 1}</h1>
          <div className="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
            <span>First to {session.winConditionCardCount ?? 0} cards wins</span><span className="size-1 rounded-full bg-muted-foreground" />
            <strong className="font-display text-accent">{currentRound?.status?.replaceAll("_", " ") ?? "Waiting"}</strong>
          </div>
        </div>
        <div className="rounded-full border-2 border-border bg-card px-4 py-2 text-sm font-semibold text-card-foreground">{session.djMode === "FIXED" ? "Fixed DJ" : "Rotating DJ"}</div>
      </header>

      <section className="relative flex flex-1 items-center justify-center">
        <p className="absolute bottom-[calc(50%+108px)] font-bold text-xs uppercase tracking-[0.24em] text-muted-foreground">Timeline</p>
        <div className="w-full max-w-[1100px] overflow-x-auto px-10 py-4 [mask-image:linear-gradient(90deg,transparent,#000_7%,#000_93%,transparent)]">
          <div className="flex w-max gap-[18px]">
            {timeline.map((card, index) => <TimelineCard key={`${card.songId}-${card.position}`} title={card.title} releaseYear={card.releaseYear} index={index} />)}
            {timeline.length === 0 ? <p className="py-12 text-sm text-muted-foreground">Your timeline will appear when the round starts.</p> : null}
          </div>
        </div>
        <button type="button" className="absolute left-0 top-1/2 flex size-10 -translate-y-1/2 items-center justify-center rounded-full border-2 border-border bg-card"><ChevronLeft className="size-5" /></button>
        <button type="button" className="absolute right-0 top-1/2 flex size-10 -translate-y-1/2 items-center justify-center rounded-full border-2 border-border bg-card"><ChevronRight className="size-5" /></button>
      </section>

      <footer className="flex flex-wrap items-center justify-between gap-4">
        <div className="inline-flex items-center gap-3 rounded-full border-2 border-border bg-card px-4 py-2">
          <UserRound className="size-4 text-primary" />
          <span className="text-xs text-muted-foreground">DJ</span><strong className="text-sm text-card-foreground">{djPlayer?.displayName ?? "Waiting"}</strong>
          <span className="h-7 w-px bg-border" />
          <span className="text-xs text-muted-foreground">Turn</span><strong className="text-sm text-card-foreground">{activePlayer?.displayName ?? "Waiting"}</strong>
        </div>
        <div className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"><Music2 className="size-4 text-warning" /> Your tokens {activePlayer?.tokenCount ?? 0}</div>
      </footer>
    </main>
  );
}
