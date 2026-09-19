"use client";

import Link from "next/link";
import { ArrowRight, Clock3, Radio } from "lucide-react";
import { usePathname } from "next/navigation";

import { useGetActiveMembership } from "@/hooks/generated/group-management/group-management";
import { useGetActiveSessionForGroup, useGetSession } from "@/hooks/generated/game-session/game-session";

const LOCKED_GROUP_STATUS = "LOCKED";

export function ActiveSessionWidget() {
  const pathname = usePathname();
  const membershipQuery = useGetActiveMembership({ query: { retry: false } });
  const groupId = membershipQuery.data?.id ?? 0;
  const sessionQuery = useGetActiveSessionForGroup(groupId, { query: { enabled: membershipQuery.data?.status === LOCKED_GROUP_STATUS, retry: false } });
  const sessionId = sessionQuery.data?.id;
  const sessionDetailsQuery = useGetSession(sessionId ?? 0, { query: { enabled: sessionId !== undefined, retry: false } });
  const currentRound = sessionDetailsQuery.data?.currentRound;
  const activePlayer = sessionDetailsQuery.data?.players?.find((player) => player.id === currentRound?.activePlayerId);

  if (!sessionId || pathname.startsWith(`/sessions/${sessionId}`)) return null;

  return <Link href={`/sessions/${sessionId}`} aria-label="Return to game in progress" className="fixed bottom-4 right-4 z-40 w-[min(310px,calc(100vw-2rem))] rounded-[16px] border-[3px] border-border bg-card p-5 shadow-[6px_6px_0_rgba(0,0,0,0.3)] transition-transform hover:-translate-y-1 sm:bottom-10 sm:right-10"><span className="flex items-center gap-2"><Radio className="size-3 text-green" /><span className="font-bold text-[13px] text-card-foreground">Game in progress</span></span><span className="mt-3 block text-[13px] text-muted-foreground">{activePlayer?.displayName ? `${activePlayer.displayName} is placing a card` : "A round is in progress"}</span><span className="mt-2 flex items-center gap-2 text-[13px] font-display text-blue"><Clock3 className="size-[18px]" />Round {currentRound?.roundNumber ?? ""}</span><span className="mt-4 inline-flex items-center gap-2 rounded-full bg-primary px-4 py-2.5 font-display text-xs text-primary-foreground">Return to game <ArrowRight className="size-4" /></span></Link>;
}
