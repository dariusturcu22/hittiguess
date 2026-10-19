"use client";

import { use } from "react";
import Link from "next/link";
import { Button } from "@/components/shadcn/button";
import { Skeleton } from "@/components/shadcn/skeleton";
import { useGameHistoryDetail, gameModeLabel, gameDurationMinutes, INTERRUPTED_GAME } from "@/hooks/game-history";

const PLAYER_LEFT = "LEFT";
const TARGET_REACHED = "TARGET_REACHED";
export default function GameHistoryDetailPage({ params }: { params: Promise<{ summaryId: string }> }) {
  const { summaryId } = use(params);
  const numericSummaryId = Number(summaryId);
  const history = useGameHistoryDetail(numericSummaryId);
  const validId = Number.isSafeInteger(numericSummaryId) && numericSummaryId > 0;
  const summary = history.data;
  return <main className="mx-auto w-full max-w-[1212px] px-14 py-10">
    <Link href="/history" className="font-semibold text-primary hover:underline">Back to history</Link>
    {history.isLoading && validId ? <Skeleton className="mt-6 h-72 w-full" /> : null}
    {history.isError || !validId ? <div role="alert" className="mt-6"><p>This game is unavailable or was not played by your account.</p>{validId ? <Button className="mt-3" onClick={() => void history.refetch()}>Retry</Button> : null}</div> : null}
    {summary ? <>
      <h1 className="mt-5 font-display text-[28px]">{summary.groupName}</h1>
      <p className="mt-3 text-muted-foreground">{new Date(summary.endedAt).toLocaleDateString()} · {gameDurationMinutes(summary)} minutes · {gameModeLabel(summary)} · Target: {summary.winTargetCards} cards</p>
      <section className="mt-6 rounded-[18px] border-2 border-border bg-card p-6">
        <h2 className="font-display text-lg">Final standings</h2>
        <p className="mt-3 text-sm text-muted-foreground">Placements, titles, artists, and bets show successes / attempts.</p>
        <p className="mt-3 text-muted-foreground">{summary.endingReason === INTERRUPTED_GAME ? "Interrupted. This game does not count as a competitive win." : summary.endingReason === TARGET_REACHED ? "Target reached. Equal card counts share a rank." : "Songs exhausted. Equal card counts share a rank."}</p>
        <table className="mt-4 w-full text-left"><thead><tr className="border-b border-border"><th className="p-4">Rank</th><th>Player</th><th>Cards</th><th>Placements</th><th>Titles</th><th>Artists</th><th>Bets won</th></tr></thead>
          <tbody>{summary.participants.map((participant) => <tr key={participant.id} className="border-b border-border last:border-0">
            <td className="p-4">{participant.cardRank}</td>
            <td className="font-semibold">{participant.displayName}{participant.isWinner ? " · Winner" : ""}{participant.participationStatus === PLAYER_LEFT ? " · Left" : ""}</td>
            <td>{participant.finalCardCount}</td><td>{participant.correctPlacements} / {participant.placementAttempts}</td>
            <td>{participant.correctTitles} / {participant.titleAttempts}<span className="block text-xs text-muted-foreground">Rank {participant.titleRank}</span></td>
            <td>{participant.correctArtists} / {participant.artistAttempts}<span className="block text-xs text-muted-foreground">Rank {participant.artistRank}</span></td>
            <td>{participant.betsWon} / {participant.betsPlaced}</td>
          </tr>)}</tbody>
        </table>
      </section>
    </> : null}
  </main>;
}
