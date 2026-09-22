"use client";

import { use, useState } from "react";
import Link from "next/link";
import { Check, Copy, Download, Loader2, Printer, RotateCcw, Trophy } from "lucide-react";

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

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

interface ResultsSummary {
  winnerLine: string;
  rankingLines: string[];
  artistsLines: string[];
  titlesLines: string[];
}

function summarizeResults(
  ranking: PlayerResultDTO[],
  mostArtistsGuessed: LeaderboardEntryDTO[],
  mostTitlesGuessed: LeaderboardEntryDTO[],
): ResultsSummary {
  const winner = ranking.find((player) => player.rank === FIRST_PLACE) ?? ranking[0];
  const nameOf = (displayName?: string | null) => displayName ?? "Player";
  return {
    winnerLine: `${nameOf(winner?.displayName)} reached ${winner?.cardCount ?? 0} cards first`,
    rankingLines: ranking.map(
      (player, index) => `${player.rank ?? index + 1}. ${nameOf(player.displayName)} - ${player.cardCount ?? 0} cards`,
    ),
    artistsLines: mostArtistsGuessed.map(
      (entry) => `${nameOf(entry.displayName)}: ${entry.value ?? 0}`,
    ),
    titlesLines: mostTitlesGuessed.map(
      (entry) => `${nameOf(entry.displayName)}: ${entry.value ?? 0}`,
    ),
  };
}

export default function SessionResultsPage({ params }: PageProps) {
  const { sessionId: sessionIdParam } = use(params);
  const sessionId = Number(sessionIdParam);
  const sessionQuery = useGetSession(sessionId, { query: { retry: false } });
  const groupId = sessionQuery.data?.groupId ?? 0;
  const resultsQuery = useGetResults(groupId, { query: { enabled: groupId > 0, retry: false } });
  const [isDownloadOpen, setIsDownloadOpen] = useState(false);
  const [copiedFeedback, setCopiedFeedback] = useState(false);

  if (!Number.isInteger(sessionId) || sessionId <= 0) return <main className="p-10 text-destructive">This game session link is invalid.</main>;
  if (sessionQuery.isLoading || resultsQuery.isLoading) return <main className="flex h-full min-h-[720px] items-center justify-center"><Loader2 className="size-8 animate-spin text-primary" aria-label="Loading results" /></main>;
  if (sessionQuery.isError || resultsQuery.isError || !resultsQuery.data) return <main className="p-10 text-destructive">Results are unavailable for this session.</main>;

  const results = resultsQuery.data;
  const ranking = results.cardCountRanking ?? [];
  const winner = ranking.find((player) => player.rank === FIRST_PLACE) ?? ranking[0];

  function downloadCsv() {
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
    setIsDownloadOpen(false);
  }

  function resultsText(): string {
    const summary = summarizeResults(ranking, results.mostArtistsGuessed ?? [], results.mostTitlesGuessed ?? []);
    const section = (title: string, lines: string[]) =>
      lines.length > 0 ? `${title}\n${lines.join("\n")}` : null;
    return [
      "hittiguess results",
      summary.winnerLine,
      "",
      section(
        "Ranking",
        summary.rankingLines,
      ),
      section(
        "Most artists guessed",
        summary.artistsLines,
      ),
      section(
        "Most titles guessed",
        summary.titlesLines,
      ),
    ]
      .filter((part) => part !== null)
      .join("\n");
  }

  async function copyResultsText() {
    await navigator.clipboard.writeText(resultsText());
    setCopiedFeedback(true);
    window.setTimeout(() => setCopiedFeedback(false), 2000);
  }

  function printResults() {
    const summary = summarizeResults(ranking, results.mostArtistsGuessed ?? [], results.mostTitlesGuessed ?? []);
    const listItems = (lines: string[]) => lines.map((line) => `<li>${escapeHtml(line)}</li>`).join("");
    const printWindow = window.open("", "_blank", "width=800,height=600");
    if (!printWindow) {
      return;
    }
    printWindow.document.write(
      `<html><head><title>hittiguess results</title></head><body style="font-family:sans-serif">`
        + `<h1>hittiguess results</h1><p>${escapeHtml(summary.winnerLine)}</p>`
        + `<h2>Ranking</h2><ol>${listItems(summary.rankingLines)}</ol>`
        + `<h2>Most artists guessed</h2><ol>${listItems(summary.artistsLines)}</ol>`
        + `<h2>Most titles guessed</h2><ol>${listItems(summary.titlesLines)}</ol>`
        + `</body></html>`,
    );
    printWindow.document.close();
    printWindow.focus();
    printWindow.print();
    setIsDownloadOpen(false);
  }

  return <main className="relative flex min-h-[720px] flex-col items-center overflow-hidden px-6 py-8 sm:px-14 sm:py-7"><header className="text-center"><Trophy className="mx-auto size-8 fill-warning text-warning" /><h1 className="mt-2 font-display text-[30px] text-foreground drop-shadow-sm">Game Over</h1><p className="mt-2 text-sm text-muted-foreground">{winner?.displayName ?? "The winner"} reached {winner?.cardCount ?? 0} cards first</p></header><section className="flex flex-1 flex-col items-center justify-center gap-10 py-10"><div className="flex items-end justify-center gap-5 sm:gap-10">{ranking.slice(0, 3).map((player, index) => { const isWinner = player.rank === FIRST_PLACE; return <div key={player.playerId} className="flex flex-col items-center"><Trophy className={`mb-1 size-5 ${isWinner ? "fill-warning text-warning" : "fill-muted-foreground text-muted-foreground"}`} /><div className={`flex items-center justify-center rounded-full font-display text-2xl text-primary-foreground ${PLAYER_COLORS[index % PLAYER_COLORS.length]} ${isWinner ? "size-[76px] ring-[3px] ring-warning" : "size-[62px]"}`}>{initial(player.displayName)}</div><strong className="mt-2 text-sm text-foreground">{player.displayName ?? "Player"}</strong><div className={`mt-2 flex w-[120px] flex-col items-center justify-center rounded-t-[18px] border-[3px] ${isWinner ? "h-[120px] border-warning bg-card" : "h-[90px] border-border bg-card"}`}><span className={`font-display text-3xl ${isWinner ? "text-warning" : "text-muted-foreground"}`}>{player.rank ?? index + 1}</span><span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">{player.cardCount ?? 0} cards</span></div></div>; })}</div><div className="flex w-full max-w-[940px] flex-col items-start justify-center gap-8 lg:flex-row lg:gap-14"><StatBoard title="MOST ARTISTS GUESSED" entries={results.mostArtistsGuessed ?? []} colorClass="text-green" /><section className="w-full max-w-[280px]"><h2 className="text-center font-display text-[11px] tracking-wide text-warning">FINAL RANKING</h2><div className="mx-auto mt-2 h-0.5 w-9 bg-warning" />{ranking.map((player, index) => <RankingRow key={player.playerId} player={player} index={index} />)}</section><StatBoard title="MOST TITLES GUESSED" entries={results.mostTitlesGuessed ?? []} colorClass="text-blue" /></div></section><footer className="flex flex-wrap items-center justify-center gap-3"><button type="button" onClick={() => setIsDownloadOpen((currentValue) => !currentValue)} className="inline-flex items-center gap-2 rounded-full border-2 border-border bg-card px-4 py-3 text-xs font-semibold text-card-foreground"><Download className="size-4" />Download results</button><Link href={`/groups/${results.groupId}`} className="inline-flex items-center gap-2 rounded-full bg-primary px-4 py-3 font-display text-xs text-primary-foreground"><RotateCcw className="size-4" />Play again</Link><Link href={`/groups/${results.groupId}`} className="rounded-full border-2 border-border bg-card px-4 py-3 text-xs font-semibold text-card-foreground">Back to lobby</Link></footer>{isDownloadOpen ? (<><button type="button" aria-label="Close download options" onClick={() => setIsDownloadOpen(false)} className="fixed inset-0 z-10 cursor-default" /><section aria-label="Download options" className="absolute bottom-24 left-1/2 z-20 w-full max-w-[320px] -translate-x-1/2 rounded-[18px] border-[3px] border-border bg-card p-5 shadow-[6px_6px_0_rgba(0,0,0,0.35)]"><div className="mb-4 flex items-center justify-between"><h2 className="font-display text-sm text-card-foreground">Download results</h2><button type="button" onClick={() => setIsDownloadOpen(false)} className="text-muted-foreground hover:text-card-foreground">Close</button></div><div className="grid gap-2"><button type="button" onClick={printResults} className="inline-flex items-center gap-2 rounded-full border-2 border-border bg-background px-4 py-2.5 text-xs font-semibold text-card-foreground hover:border-primary/60"><Printer className="size-4" />Download PDF</button><button type="button" onClick={copyResultsText} className="inline-flex items-center gap-2 rounded-full border-2 border-border bg-background px-4 py-2.5 text-xs font-semibold text-card-foreground hover:border-primary/60">{copiedFeedback ? <Check className="size-4 text-primary" /> : <Copy className="size-4" />}{copiedFeedback ? "Copied" : "Copy as text"}</button><button type="button" onClick={downloadCsv} className="inline-flex items-center gap-2 rounded-full border-2 border-border bg-background px-4 py-2.5 text-xs font-semibold text-card-foreground hover:border-primary/60"><Download className="size-4" />Download CSV</button></div></section></>) : null}</main>;
}
