"use client";

import React from "react";
import { SongPipelineActions } from "./song-pipeline-actions";
import { SourceFindings } from "./source-findings";
import {
  useUpdateCatalogSong,
  useDismissProposal,
} from "@/hooks/generated/admin-song-pipeline/admin-song-pipeline";
import { useAdminMutationFeedback } from "../backfill-queue/pipeline-ui";

import { useSongDetail } from "@/hooks/generated/admin-song-catalog/admin-song-catalog";
import type { AdminQueueHistoryEntryDTO } from "@/hooks/models/adminQueueHistoryEntryDTO";
import type { AdminSongDetailDTO } from "@/hooks/models/adminSongDetailDTO";
import { Skeleton } from "@/components/shadcn/skeleton";

import {
  detailMetaLine,
  formatCount,
  formatHistoryTime,
  presentStatus,
  queueOriginLabel,
  queueStatusLabel,
} from "./song-catalog-presentation";

function StatusPill({ status }: { status: AdminSongDetailDTO["status"] }) {
  const { label, pillClassName } = presentStatus(status);
  return (
    <span
      className={`inline-block shrink-0 whitespace-nowrap rounded-full px-2.5 py-1 font-display text-[9px] leading-none ${pillClassName}`}
    >
      {label}
    </span>
  );
}

function historyEntryText(entry: AdminQueueHistoryEntryDTO): string {
  const parts = [
    `${queueOriginLabel(entry.origin)}, ${queueStatusLabel(entry.status)}`,
  ];
  if (entry.processedAt) {
    parts.push(`finished ${formatHistoryTime(entry.processedAt)}`);
  }
  if (entry.provisionalYear != null) {
    parts.push(`provisional year ${entry.provisionalYear}`);
  }
  if (entry.patientYear != null) {
    parts.push(`full pipeline year ${entry.patientYear}`);
  }
  const summary = parts.join(", ");
  return entry.status === "FAILED" && entry.failureReason
    ? `${summary}. ${entry.failureReason}`
    : summary;
}

function StoredValueRow({
  label,
  value,
}: {
  label: string;
  value: React.ReactNode;
}) {
  return (
    <div className="flex items-center justify-between gap-3 py-1">
      <span className="text-muted-foreground">{label}</span>
      <span className="min-w-0 truncate font-semibold text-card-foreground">
        {value}
      </span>
    </div>
  );
}

function DetailBody({
  detail,
  onDelete,
}: {
  detail: AdminSongDetailDTO;
  onDelete: () => void;
}) {
  const feedback = useAdminMutationFeedback();
  const updateSong = useUpdateCatalogSong({ mutation: feedback });
  const dismiss = useDismissProposal({ mutation: feedback });
  const isPending = updateSong.isPending || dismiss.isPending;

  const artistNames =
    detail.artists.length > 0 ? detail.artists.join(", ") : "unknown";
  const queueHistory = detail.queueHistory;
  return (
    <>
      <div className="mb-[18px] flex items-start justify-between">
        <div className="min-w-0 pr-8">
          <h2 className="break-words font-display text-[22px] text-accent">
            {detail.title ?? detail.youtubeId}
          </h2>
          <div className="mt-[3px] break-words text-[13px] text-muted-foreground">
            {artistNames}
          </div>
        </div>
      </div>
      <div className="mb-[18px] flex flex-wrap items-center gap-2.5">
        <StatusPill status={detail.status} />
        <span className="text-xs text-muted-foreground">
          {detailMetaLine(detail)}
        </span>
      </div>

      {detail.proposedReleaseYear != null && (
        <div className="mb-4 rounded-[12px] border-2 border-accent/40 bg-accent/10 p-3.5 text-xs">
          <div className="flex items-center justify-between">
            <div>
              <span className="font-semibold text-accent">Proposed release year:</span>{" "}
              <span className="font-display text-sm font-bold text-card-foreground">
                {detail.proposedReleaseYear}
              </span>
              <span className="ml-2 text-muted-foreground">
                (stored: {detail.releaseYear ?? "pending"})
              </span>
            </div>
          </div>
          <div className="mt-3 flex gap-2">
            <button
              type="button"
              disabled={isPending}
              onClick={() => {
                if (detail.proposedReleaseYear != null) {
                  updateSong.mutate({
                    id: detail.songId,
                    data: { releaseYear: detail.proposedReleaseYear },
                  });
                }
              }}
              className="rounded-full bg-accent px-3 py-1 font-display text-[10px] font-semibold text-accent-foreground hover:opacity-90 disabled:opacity-50"
            >
              Accept
            </button>
            <button
              type="button"
              disabled={isPending}
              onClick={() => {
                dismiss.mutate({ id: detail.songId });
              }}
              className="rounded-full bg-secondary px-3 py-1 font-display text-[10px] text-secondary-foreground hover:bg-secondary/80 disabled:opacity-50"
            >
              Dismiss
            </button>
          </div>
        </div>
      )}

      <SourceFindings
        sources={detail.sourceFindings}
        reason={detail.reviewReason}
        releaseYear={detail.releaseYear}
      />
      <details className="mb-4 text-xs">
        <summary className="cursor-pointer text-muted-foreground">
          Stored values
        </summary>
        <div className="mb-2 font-display text-xs text-card-foreground">
          Stored values
        </div>
        <div className="mb-4 rounded-[12px] bg-background px-3.5 dark:bg-card py-2.5 text-xs">
          <StoredValueRow label="Stored year" value={detail.releaseYear ?? "pending"} />
          {detail.proposedReleaseYear != null && (
            <StoredValueRow label="Proposed year" value={detail.proposedReleaseYear} />
          )}
          <StoredValueRow label="YouTube ID" value={detail.youtubeId} />
          <StoredValueRow
            label="Verification status"
            value={detail.verificationStatus}
          />
          <StoredValueRow
            label="Confidence"
            value={detail.confidence ?? "none"}
          />
          <StoredValueRow
            label="Wikidata sitelinks"
            value={
              detail.wikidataSitelinksCount != null
                ? formatCount(detail.wikidataSitelinksCount)
                : "none"
            }
          />
          <StoredValueRow
            label="Open reports"
            value={formatCount(detail.openReportCount)}
          />
        </div>
      </details>

      <div className="mb-2 font-display text-xs text-card-foreground">
        History
      </div>
      {queueHistory.length === 0 ? (
        <div className="text-[11px] leading-[1.9] text-muted-foreground">
          No queue history for this YouTube ID.
        </div>
      ) : (
        <ol
          className="text-[11px] leading-[1.9] text-muted-foreground"
          aria-label="Queue history"
        >
          {queueHistory.map((entry) => (
            <li key={`${entry.origin}:${entry.id}:${entry.enqueuedAt}`}>
              <span className="mr-2.5">
                {formatHistoryTime(entry.enqueuedAt)}
              </span>
              {historyEntryText(entry)}
            </li>
          ))}
        </ol>
      )}
      <SongPipelineActions detail={detail} onDelete={onDelete} />
    </>
  );
}

export function SongDetailDrawer({
  songId,
  onClose,
}: {
  songId: number;
  onClose: () => void;
}) {
  const { data: detail, isLoading, isError, refetch } = useSongDetail(songId);

  const closeButton = React.useRef<HTMLButtonElement>(null);
  React.useEffect(() => {
    const previousFocus = document.activeElement;
    closeButton.current?.focus();
    function closeOnEscape(event: KeyboardEvent) {
      if (
        event.key === "Escape" &&
        !document.querySelector('[role="alertdialog"]')
      ) {
        onClose();
      }
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => {
      window.removeEventListener("keydown", closeOnEscape);
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, [onClose]);

  return (
    <aside
      role="dialog"
      aria-label="Song detail"
      className="absolute inset-y-0 right-0 z-10 box-border flex w-[min(520px,100%)] flex-col overflow-y-auto overflow-x-hidden scrollbar-thin-visible border-l-[3px] border-border-strong bg-surface-sunken px-7 pt-7 pb-6 shadow-[-8px_0_0_var(--shadow-color)] short:pt-6 short:pb-5 max-xl:px-6"
    >
      <button
        ref={closeButton}
        type="button"
        onClick={onClose}
        aria-label="Close song detail"
        className="absolute top-7 right-7 text-xl leading-none text-icon-muted hover:text-card-foreground"
      >
        &times;
      </button>
      {isLoading ? (
        <div
          data-testid="song-detail-skeleton"
          className="flex flex-col gap-3 pr-8"
        >
          <Skeleton className="h-7 w-2/3 rounded-md" />
          <Skeleton className="h-4 w-1/3 rounded-md" />
          <Skeleton className="h-24 w-full rounded-md" />
        </div>
      ) : isError || !detail ? (
        <div className="pr-8 text-[13px] text-muted-foreground">
          <p className="mb-3 text-destructive">Couldn&apos;t load this song.</p>
          <button
            type="button"
            onClick={() => void refetch()}
            className="rounded-full bg-secondary px-3 py-1.5 font-display text-[9px] text-secondary-foreground"
          >
            Retry
          </button>
        </div>
      ) : (
        <DetailBody key={detail.songId} detail={detail} onDelete={onClose} />
      )}
    </aside>
  );
}
