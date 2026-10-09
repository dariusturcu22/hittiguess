"use client";

import { useState } from "react";
import Link from "next/link";
import { Button } from "@/components/shadcn/button";
import { Skeleton } from "@/components/shadcn/skeleton";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import { useGameHistory, useHistoryStatistics, gameModeLabel, gameDurationMinutes,
  HISTORY_PAGE_SIZE, INTERRUPTED_GAME, PERCENT_SCALE } from "@/hooks/game-history";

export default function GameHistoryPage() {
  const [page, setPage] = useState(0);
  const history = useGameHistory(page);
  const statistics = useHistoryStatistics();
  const { data: user } = useGetCurrentUser();
  const totals = statistics.data;
  const winRate = totals && totals.gamesPlayed > 0 ? Math.round(totals.wins / totals.gamesPlayed * PERCENT_SCALE) : 0;

  return <main className="mx-auto w-full max-w-[1212px] px-14 py-10">
    <h1 className="font-display text-[28px]">Game history</h1>
    <p className="mt-3 text-muted-foreground">Your games and results, including games from groups that have expired.</p>
    <div className="mt-6 grid grid-cols-3 gap-6">
      {[
        { label: "Games played", value: totals?.gamesPlayed },
        { label: "Wins", value: totals?.wins },
        { label: "Win rate", value: totals ? `${winRate}%` : undefined },
      ].map((metric) => <div key={metric.label} className="rounded-[18px] border-2 border-border bg-card p-6">
        {statistics.isLoading ? <Skeleton className="h-9 w-16" /> : <strong className="block font-display text-[26px]">{metric.value ?? "Unavailable"}</strong>}
        <p>{metric.label}</p>
      </div>)}
    </div>
    {statistics.isError ? <p role="alert" className="mt-3 text-destructive">Statistics could not be loaded. <Button variant="ghost" onClick={() => void statistics.refetch()}>Retry statistics</Button></p> : null}
    <section aria-label="Past games" className="mt-6 rounded-[18px] border-2 border-border bg-card p-6">
      {history.isLoading ? <div role="status"><Skeleton className="h-48 w-full" /><span className="sr-only">Loading game history</span></div> : null}
      {history.isError ? <div role="alert"><p>Game history could not be loaded.</p><Button className="mt-3" onClick={() => void history.refetch()}>Retry</Button></div> : null}
      {history.data?.items.length === 0 ? <p>{page === 0 ? "No games yet. Completed and interrupted games will appear here." : "No games on this page."}</p> : null}
      {history.data && history.data.items.length > 0 ? <table className="w-full text-left">
        <thead><tr className="border-b border-border"><th className="p-4">Game</th><th>Mode</th><th>Result</th><th>Cards</th><th><span className="sr-only">Details</span></th></tr></thead>
        <tbody>{history.data.items.map((summary) => {
          const participant = summary.participants.find((entry) => entry.userId === user?.id);
          const winnerCount = summary.participants.filter((entry) => entry.isWinner).length;
          const result = summary.endingReason === INTERRUPTED_GAME ? "Interrupted"
            : participant?.isWinner ? winnerCount > 1 ? "Joint winner" : "Winner" : participant ? `Rank ${participant.cardRank}` : "Results";
          return <tr key={summary.id} className="border-b border-border last:border-0">
            <td className="p-4 font-semibold">{summary.groupName}<span className="block text-xs font-normal text-muted-foreground">{new Date(summary.endedAt).toLocaleDateString()} · {gameDurationMinutes(summary)} minutes</span></td>
            <td>{gameModeLabel(summary)}</td><td>{result}</td><td>{participant?.finalCardCount ?? "Unavailable"}</td>
            <td><Link aria-label={`View results for ${summary.groupName}`} className="font-semibold text-primary underline-offset-4 hover:underline" href={`/history/${summary.id}`}>View results</Link></td>
          </tr>;
        })}</tbody>
      </table> : null}
    </section>
    <nav aria-label="History pages" className="mt-6 flex items-center justify-between">
      <Button variant="outline" disabled={page === 0 || history.isFetching} onClick={() => setPage((currentPage) => currentPage - 1)}>Previous</Button>
      <span>Page {page + 1}</span>
      <Button variant="outline" disabled={!history.data || (page + 1) * HISTORY_PAGE_SIZE >= history.data.total || history.isFetching} onClick={() => setPage((currentPage) => currentPage + 1)}>Next</Button>
    </nav>
  </main>;
}
