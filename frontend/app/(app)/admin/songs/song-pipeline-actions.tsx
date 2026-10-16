"use client";

import React from "react";
import { toast } from "sonner";
import {
  useUpdateCatalogSong,
  useDeleteCatalogSong,
  useRerunSongPipeline,
} from "@/hooks/generated/admin-song-pipeline/admin-song-pipeline";
import { UpdateCatalogSongRequestVerificationStatus } from "@/hooks/models/updateCatalogSongRequestVerificationStatus";
import type { AdminSongDetailDTO } from "@/hooks/models/adminSongDetailDTO";
import {
  ConfirmAction,
  inputClass,
  useAdminMutationFeedback,
} from "../backfill-queue/pipeline-ui";

const MINIMUM_RELEASE_YEAR = 1000;
export function SongPipelineActions({
  detail,
  onDelete,
}: {
  detail: AdminSongDetailDTO;
  onDelete: () => void;
}) {
  const feedback = useAdminMutationFeedback();
  const rerun = useRerunSongPipeline({ mutation: feedback });
  const update = useUpdateCatalogSong({ mutation: feedback });
  const remove = useDeleteCatalogSong({ mutation: feedback });
  const [year, setYear] = React.useState(String(detail.releaseYear ?? ""));
  const [status, setStatus] = React.useState(detail.verificationStatus);
  const pending = rerun.isPending || update.isPending || remove.isPending;
  const id = detail.songId;
  return (
    <div className="mt-auto flex flex-wrap gap-2.5 pt-4">
      <ConfirmAction
        label="Re-run pipeline"
        primary
        title="Re-run this song?"
        description="The full metadata pipeline will run again for this video."
        danger={false}
        pending={pending}
        onConfirm={() => rerun.mutateAsync({ id })}
      />
      <ConfirmAction
        label="Set year"
        title="Set release year"
        description="The catalog year will change. Verification status is kept."
        danger={false}
        pending={pending}
        onConfirm={async () => {
          const releaseYear = Number(year);
          if (
            !Number.isInteger(releaseYear) ||
            releaseYear < MINIMUM_RELEASE_YEAR ||
            releaseYear > new Date().getFullYear()
          ) {
            toast.error("Enter a valid release year.");
            throw new Error("Invalid release year");
          }
          return update.mutateAsync({ id, data: { releaseYear } });
        }}
      >
        <label className="text-sm">
          Release year
          <input
            type="number"
            className={`${inputClass} mt-2`}
            min={MINIMUM_RELEASE_YEAR}
            max={new Date().getFullYear()}
            value={year}
            onChange={(event) => setYear(event.target.value)}
          />
        </label>
      </ConfirmAction>
      <ConfirmAction
        label="Set status"
        title="Set verification status"
        description="Verified songs need a release year and become eligible for embedding."
        danger={false}
        pending={pending}
        onConfirm={() =>
          update.mutateAsync({ id, data: { verificationStatus: status } })
        }
      >
        <label className="text-sm">
          Verification status
          <select
            className={`${inputClass} mt-2`}
            value={status}
            onChange={(event) => setStatus(event.target.value as typeof status)}
          >
            {Object.values(UpdateCatalogSongRequestVerificationStatus).map(
              (value) => (
                <option key={value} value={value}>
                  {value.replaceAll("_", " ")}
                </option>
              ),
            )}
          </select>
        </label>
      </ConfirmAction>
      <ConfirmAction
        label="Delete"
        title="Delete this song?"
        description="The song and its playlist links will be removed, and unfinished pipeline work cancelled. This cannot be undone. Songs used by a game cannot be deleted."
        pending={pending}
        onConfirm={async () => {
          await remove.mutateAsync({ id });
          onDelete();
        }}
      />
    </div>
  );
}
