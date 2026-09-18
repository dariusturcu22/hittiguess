"use client";

import { use } from "react";
import Link from "next/link";
import { Download, Loader2, RotateCcw, Trophy } from "lucide-react";

import { useGetResults, useGetSession } from "@/hooks/generated/game-session/game-session";
import type { LeaderboardEntryDTO } from "@/hooks/models/leaderboardEntryDTO";
import type { PlayerResultDTO } from "@/hooks/models/playerResultDTO";

const FIRST_PLACE = 1;
const PLAYER_COLORS = ["bg-peach", "bg-blue", "bg-warning", "bg-pink", "bg-green", "bg-accent"];
const RESULTS_FILE_PREFIX = "hittiguess-results";
const CSV_MEDIA_TYPE = "text/csv;charset=utf-8";

interface PageProps { params: Promise<{ sessionId: string }>; }

function initial(name?: string): string { return name?.trim().charAt(0).toUpperCase() || "?"; }

function RankingRow({ player, index }: { player: PlayerResultDTO; index: number }) {
  return <div className="flex items-center gap-3 border-b border-border/70 py-3 last:border-0"><span className="w-5 font-display text-sm text-muted-foreground">{player.rank ?? index + 1}</span><span className={`flex size-8 items-center justify-center rounded-full font-display text-xs text-primary-foreground ${PLAYER_COLORS[index % PLAYER_COLORS.length]}`}>{initial(player.displayName)}</span><span className="flex-1 text-sm font-semibold text-card-foreground">{player.displayName ?? "Player"}</span><span className="font-display text-base text-card-foreground">{player.cardCount ?? 0}</span></div>;
}

function StatBoard({ title, entries, colorClass }: { title: string; entries: LeaderboardEntryDTO[]; colorClass: string }) {
  return <section className="w-full max-w-[280px]"><h2 className={`font-display text-[11px] tracking-wide ${colorClass}`}>{title}</h2><div className={`mt-2 h-0.5 w-9 ${colorClass.replace("text-", "bg-")}`} />{entries.map((entry, index) => <div key={`${entry.playerId}-${entry.displayName}`} className="flex items-center gap-2 border-b border-border/70 py-2.5 last:border-0"><span className={`flex size-7 items-center justify-center rounded-full font-display text-[10px] text-primary-foreground ${PLAYER_COLORS[index % PLAYER_COLORS.length]}`}>{initial(entry.displayName)}</span><span className="flex-1 text-sm text-card-foreground">{entry.displayName ?? "Player"}</span><strong className={colorClass}>{entry.value ?? 0}</strong></div>)}</section>;
}

function csvCell(value: string | number | undefined): string {
  return `"${String(value ?? "").replaceAll('"', '""')}"`;
}

export default function SessionResultsPage({ params }: PageProps) {
  const { sessionId: sessionIdParam } = use(params);
  const sessionId = Number(sessionIdParam);
  const sessionQuery = useGetSession(sessionId, { query: { retry: false } });
  const groupId = sessionQuery.data?.groupId ?? 0;
  const resultsQuery = useGetResults(groupId, { query: { enabled: groupId > 0, retry: false } });

  if (!Number.isInteger(sessionId) || sessionId <= 0) return <main className="p-10 text-destructive">This game session link is invalid.</main>;
  if (sessionQuery.isLoading || resultsQuery.isLoading) return <main className="flex h-full min-h-[720px] items-center justify-center"><Loader2 className="size-8 animate-spin text-primary" aria-label="Loading results" /></main>;
  if (sessionQuery.isError || resultsQuery.isError || !resultsQuery.data) return <main className="p-10 text-destructive">Results are unavailable for this session.</main>;

  const results = resultsQuery.data;
  const ranking = results.cardCountRanking ?? [];
  const winner = ranking.find((player) => player.rank === FIRST_PLACE) ?? ranking[0];

  function downloadResults() {
    const header = ["Rank", "Player", "Cards"];
    const rows = ranking.map((player, index) => [
      player.rank ?? index + 1,
      player.displayName ?? "Player",
      player.cardCount ?? 0,
    ]);
    const csv = [header, ...rows].map((row) => row.map(csvCell).join(",")).join("\n");
    const file = new Blob([csv], { type: CSV_MEDIA_TYPE });
    const downloadUrl = URL.createObjectURL(file);
    const link = document.createElement("a");
    link.href = downloadUrl;
    link.download = `${RESULTS_FILE_PREFIX}-${sessionId}.csv`;
    link.click();
    URL.revokeObjectURL(downloadUrl);
  }

  return <main className="relative flex min-h-[720px] flex-col items-center overflow-hidden px-6 py-8 sm:px-14 sm:py-7"><header className="text-center"><Trophy className="mx-auto size-8 fill-warning text-warning" /><h1 className="mt-2 font-display text-[30px] text-foreground drop-shadow-sm">Game Over</h1><p className="mt-2 text-sm text-muted-foreground">{winner?.displayName ?? "The winner"} reached {winner?.cardCount ?? 0} cards first</p></header><section className="flex flex-1 flex-col items-center justify-center gap-10 py-10"><div className="flex items-end justify-center gap-5 sm:gap-10">{ranking.slice(0, 3).map((player, index) => { const isWinner = player.rank === FIRST_PLACE; return <div key={player.playerId} className="flex flex-col items-center"><Trophy className={`mb-1 size-5 ${isWinner ? "fill-warning text-warning" : "fill-muted-foreground text-muted-foreground"}`} /><div className={`flex items-center justify-center rounded-full font-display text-2xl text-primary-foreground ${PLAYER_COLORS[index % PLAYER_COLORS.length]} ${isWinner ? "size-[76px] ring-[3px] ring-warning" : "size-[62px]"}`}>{initial(player.displayName)}</div><strong className="mt-2 text-sm text-foreground">{player.displayName ?? "Player"}</strong><div className={`mt-2 flex w-[120px] flex-col items-center justify-center rounded-t-[18px] border-[3px] ${isWinner ? "h-[120px] border-warning bg-card" : "h-[90px] border-border bg-card"}`}><span className={`font-display text-3xl ${isWinner ? "text-warning" : "text-muted-foreground"}`}>{player.rank ?? index + 1}</span><span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">{player.cardCount ?? 0} cards</span></div></div>; })}</div><div className="flex w-full max-w-[940px] flex-col items-start justify-center gap-8 lg:flex-row lg:gap-14"><StatBoard title="MOST ARTISTS GUESSED" entries={results.mostArtistsGuessed ?? []} colorClass="text-green" /><section className="w-full max-w-[280px]"><h2 className="text-center font-display text-[11px] tracking-wide text-warning">FINAL RANKING</h2><div className="mx-auto mt-2 h-0.5 w-9 bg-warning" />{ranking.map((player, index) => <RankingRow key={player.playerId} player={player} index={index} />)}</section><StatBoard title="MOST TITLES GUESSED" entries={results.mostTitlesGuessed ?? []} colorClass="text-blue" /></div></section><footer className="flex flex-wrap items-center justify-center gap-3"><button type="button" onClick={downloadResults} className="inline-flex items-center gap-2 rounded-full border-2 border-border bg-card px-4 py-3 text-xs font-semibold text-card-foreground"><Download className="size-4" />Download results</button><Link href={`/groups/${results.groupId}`} className="inline-flex items-center gap-2 rounded-full bg-primary px-4 py-3 font-display text-xs text-primary-foreground"><RotateCcw className="size-4" />Play again</Link><Link href={`/groups/${results.groupId}`} className="rounded-full border-2 border-border bg-card px-4 py-3 text-xs font-semibold text-card-foreground">Back to lobby</Link></footer></main>;
}
