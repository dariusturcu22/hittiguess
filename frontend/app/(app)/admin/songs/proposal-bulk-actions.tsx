"use client";

import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import {
  useAcceptProposals,
  useDismissProposals,
} from "@/hooks/generated/admin-song-pipeline/admin-song-pipeline";
import type { ProposalBatchResultDTO } from "@/hooks/models/proposalBatchResultDTO";

import { ConfirmAction, useAdminMutationFeedback } from "../backfill-queue/pipeline-ui";
import { isAdminQueryKey } from "../live-admin-data";

export function proposalBatchSummary(
  verb: "Accepted" | "Dismissed",
  result: ProposalBatchResultDTO,
) {
  const skippedSongIds = result.skippedSongIds ?? [];
  const summary = `${verb} ${result.affected ?? 0}, skipped ${skippedSongIds.length}`;
  return {
    summary,
    skippedDescription:
      skippedSongIds.length > 0
        ? `Skipped song IDs: ${skippedSongIds.join(", ")}`
        : undefined,
  };
}

function songCountLabel(count: number) {
  return `${count} ${count === 1 ? "song" : "songs"}`;
}

export function ProposalBulkActions({
  selectedSongIds,
  onDone,
}: {
  selectedSongIds: number[];
  onDone: () => void;
}) {
  const queryClient = useQueryClient();
  const { onError } = useAdminMutationFeedback();
  const count = selectedSongIds.length;

  async function refreshAndReport(verb: "Accepted" | "Dismissed", result: ProposalBatchResultDTO) {
    await queryClient.invalidateQueries({
      predicate: (query) => isAdminQueryKey(query.queryKey),
    });
    const { summary, skippedDescription } = proposalBatchSummary(verb, result);
    toast.success(summary, { description: skippedDescription });
    onDone();
  }

  const accept = useAcceptProposals({
    mutation: { onSuccess: (result) => refreshAndReport("Accepted", result), onError },
  });
  const dismiss = useDismissProposals({
    mutation: { onSuccess: (result) => refreshAndReport("Dismissed", result), onError },
  });
  const pending = accept.isPending || dismiss.isPending;
  const idleWithoutSelection = pending || count === 0;

  return (
    <div className="mb-3.5 flex shrink-0 flex-wrap items-center gap-2" role="group" aria-label="Proposed year actions">
      <span className="text-xs font-semibold text-muted-foreground">{count} selected</span>
      <ConfirmAction
        label="Accept selected"
        title={`Accept ${songCountLabel(count)}?`}
        description={`The release year of ${songCountLabel(count)} changes to the proposed year. Songs in an active game are skipped.`}
        pending={idleWithoutSelection}
        danger={false}
        primary
        onConfirm={() => accept.mutateAsync({ data: { songIds: selectedSongIds } })}
      />
      <ConfirmAction
        label="Dismiss selected"
        title={`Dismiss ${songCountLabel(count)}?`}
        description={`The proposed year of ${songCountLabel(count)} is discarded. Their release years stay as they are.`}
        pending={idleWithoutSelection}
        danger={false}
        onConfirm={() => dismiss.mutateAsync({ data: { songIds: selectedSongIds } })}
      />
    </div>
  );
}
