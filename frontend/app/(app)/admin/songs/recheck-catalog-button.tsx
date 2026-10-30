"use client";

import { toast } from "sonner";
import { useQueryClient } from "@tanstack/react-query";
import { useRecheckSongs } from "@/hooks/generated/admin-song-pipeline/admin-song-pipeline";
import { isAdminQueryKey } from "../live-admin-data";
import {
  ConfirmAction,
  count,
  useAdminMutationFeedback,
} from "../backfill-queue/pipeline-ui";

export function recheckCatalogDescription(songCount?: number) {
  const scope =
    songCount == null
      ? "Every song in the catalog"
      : `Up to ${count(songCount)} songs in the catalog`;
  return `${scope} will be queued for a new run through the metadata pipeline, using the external sources and AI. The runs go in at low priority behind user imports, so a large catalog takes a while to drain. Songs used by an active game are skipped, and songs that already have a run in progress are not queued twice. Verified songs keep their year and get a proposed year to review when the new result differs.`;
}

export function RecheckCatalogButton({ songCount }: { songCount?: number }) {
  const feedback = useAdminMutationFeedback();
  const cache = useQueryClient();
  const recheck = useRecheckSongs({ mutation: { onError: feedback.onError } });
  return (
    <ConfirmAction
      label="Recheck whole catalog"
      title="Recheck the whole catalog?"
      description={recheckCatalogDescription(songCount)}
      danger={false}
      pending={recheck.isPending}
      onConfirm={async () => {
        const result = await recheck.mutateAsync({ data: { all: true } });
        await cache.invalidateQueries({
          predicate: (query) => isAdminQueryKey(query.queryKey),
        });
        toast.success(
          `Queued ${count(result.affected ?? 0)} songs for a recheck.`,
        );
        return result;
      }}
    />
  );
}
