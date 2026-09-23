"use client";

import React from "react";
import { toast } from "sonner";

import {
  useReviewQueue,
  useResolve,
  useDismiss,
} from "@/hooks/generated/admin-song-report-review/admin-song-report-review";
import type { AdminReviewItemDTO } from "@/hooks/models/adminReviewItemDTO";
import type { AdminReviewItemDTOVerificationStatus as VerificationStatus } from "@/hooks/models/adminReviewItemDTOVerificationStatus";
import { Skeleton } from "@/components/shadcn/skeleton";

import {
  presentPriorityTier,
  toResolveVerificationStatus,
  VERIFICATION_STATUS_OPTIONS,
} from "./report-queue-presentation";

function TierChip({ tier }: { tier: AdminReviewItemDTO["priorityTier"] }) {
  const { label, chipClassName } = presentPriorityTier(tier);
  return (
    <span
      className={`font-display text-[9px] leading-none px-[9px] py-1 rounded-full whitespace-nowrap shrink-0 ${chipClassName}`}
    >
      {label}
    </span>
  );
}

function QueueRow({
  item,
  selected,
  onSelect,
}: {
  item: AdminReviewItemDTO;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={`flex w-full items-center gap-[14px] px-[18px] py-[13px] border-b-2 border-background text-left last:border-b-0 cursor-pointer ${
        selected ? "bg-accent/10" : ""
      }`}
    >
      <TierChip tier={item.priorityTier} />
      <span className="flex-1 min-w-0">
        <span className="block text-sm text-card-foreground truncate">
          {item.songTitle}
        </span>
        <span className="mt-0.5 block text-xs text-muted-foreground truncate">
          {item.artistName || "Unknown artist"} · {item.openReportCount ?? 0} reports
        </span>
      </span>
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="text-muted-foreground shrink-0"
        aria-hidden="true"
      >
        <polyline points="9 18 15 12 9 6" />
      </svg>
    </button>
  );
}

function SignalRow({
  label,
  value,
  emphasisClassName,
}: {
  label: string;
  value: React.ReactNode;
  emphasisClassName?: string;
}) {
  return (
    <div className="flex items-center justify-between py-[9px] border-b border-secondary text-[12.5px] last:border-b-0">
      <span className="text-muted-foreground">{label}</span>
      <span className={`font-bold text-card-foreground ${emphasisClassName ?? ""}`}>
        {value}
      </span>
    </div>
  );
}

function ReportDetail({
  item,
  onResolved,
}: {
  item: AdminReviewItemDTO;
  onResolved: () => void;
}) {
  const { label: tierLabel } = presentPriorityTier(item.priorityTier);
  const resolveMutation = useResolve();
  const dismissMutation = useDismiss();

  const [correctedYear, setCorrectedYear] = React.useState<string>(
    item.releaseYear != null ? String(item.releaseYear) : "",
  );
  const [status, setStatus] = React.useState<VerificationStatus>(
    item.verificationStatus ?? VERIFICATION_STATUS_OPTIONS[0],
  );

  React.useEffect(() => {
    setCorrectedYear(item.releaseYear != null ? String(item.releaseYear) : "");
    setStatus(item.verificationStatus ?? VERIFICATION_STATUS_OPTIONS[0]);
  }, [item.songId, item.releaseYear, item.verificationStatus]);

  const songId = item.songId;
  const mutating = resolveMutation.isPending || dismissMutation.isPending;

  const latestReport = item.openReports?.[item.openReports.length - 1];

  function handleResolve() {
    if (songId == null) {
      return;
    }
    const parsedYear = Number.parseInt(correctedYear, 10);
    resolveMutation.mutate(
      {
        songId,
        data: {
          correctedYear: Number.isNaN(parsedYear) ? undefined : parsedYear,
          verificationStatus: toResolveVerificationStatus(status),
        },
      },
      {
        onSuccess: () => {
          onResolved();
          toast.success("Report resolved");
        },
        onError: () => toast.error("Couldn't resolve that report. Try again."),
      },
    );
  }

  function handleDismiss() {
    if (songId == null) {
      return;
    }
    dismissMutation.mutate(
      { songId },
      {
        onSuccess: () => {
          onResolved();
          toast.success("Report dismissed");
        },
        onError: () => toast.error("Couldn't dismiss that report. Try again."),
      },
    );
  }

  return (
    <div className="flex-1 min-w-0 bg-card border-[3px] border-border-strong rounded-xl shadow-lg box-border px-[30px] py-[26px] overflow-y-auto">
      <div className="flex items-center gap-[10px] mb-1">
        <div className="font-display text-[22px] text-card-foreground">
          {item.songTitle}
        </div>
        <TierChip tier={item.priorityTier} />
      </div>
      <div className="text-[13px] text-muted-foreground mb-[22px]">
        {item.artistName || "Unknown artist"} · currently locked at {item.releaseYear ?? "unknown"},{" "}
        {item.verificationStatus} &middot; {tierLabel}
      </div>

      <div className="bg-background rounded-md px-5 py-4 mb-[22px]">
        <SignalRow label="Reports" value={item.openReportCount ?? 0} />
        <SignalRow
          label="Convergence"
          value={
            item.reportsConverge && item.convergingYear != null
              ? `${item.convergingReportCount ?? 0} of ${item.openReportCount ?? 0} agree on ${item.convergingYear}`
              : "No convergence"
          }
          emphasisClassName={
            item.reportsConverge ? "text-primary" : "text-muted-foreground"
          }
        />
        <SignalRow
          label="Community confirmations"
          value={item.confirmationCount ? item.confirmationCount : "None yet"}
        />
      </div>

      {latestReport ? (
        <div className="text-[13px] text-card-foreground leading-[1.7] mb-[22px]">
          <strong>Latest report:</strong> &ldquo;{latestReport.message}&rdquo;
          {latestReport.suggestedCorrectYear != null ? (
            <> (suggested {latestReport.suggestedCorrectYear})</>
          ) : null}
          {latestReport.sources ? (
            <div className="mt-2 text-muted-foreground">
              Sources: {latestReport.sources}
            </div>
          ) : null}
        </div>
      ) : (
        <div className="text-[13px] text-muted-foreground mb-[22px]">
          No open reports on this song.
        </div>
      )}

      <div className="mb-[18px]">
        <label
          htmlFor="corrected-year"
          className="block mb-1.5 text-[11px] font-semibold uppercase tracking-[0.5px] text-muted-foreground"
        >
          Correct release year
        </label>
        <input
          id="corrected-year"
          type="text"
          inputMode="numeric"
          value={correctedYear}
          onChange={(event) => setCorrectedYear(event.target.value)}
          className="w-full box-border bg-background border-2 border-secondary rounded-md px-[14px] py-3 font-sans text-sm text-card-foreground"
        />
      </div>
      <div className="mb-6">
        <label
          htmlFor="verification-status"
          className="block mb-1.5 text-[11px] font-semibold uppercase tracking-[0.5px] text-muted-foreground"
        >
          Verification status
        </label>
        <select
          id="verification-status"
          value={status}
          onChange={(event) =>
            setStatus(event.target.value as VerificationStatus)
          }
          className="w-full box-border bg-background border-2 border-secondary rounded-md px-[14px] py-3 font-sans font-semibold text-sm text-card-foreground"
        >
          {VERIFICATION_STATUS_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </div>

      <div className="flex gap-3">
        <button
          type="button"
          onClick={handleResolve}
          disabled={mutating || songId == null}
          className="flex-1 font-display text-[13px] text-primary-foreground bg-primary py-[13px] rounded-full shadow-sm box-border cursor-pointer text-center disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {resolveMutation.isPending ? "Resolving..." : "Resolve"}
        </button>
        <button
          type="button"
          onClick={handleDismiss}
          disabled={mutating || songId == null}
          className="flex-1 text-center py-[13px] rounded-full border-2 border-secondary font-sans font-semibold text-[13px] text-muted-foreground cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {dismissMutation.isPending ? "Dismissing..." : "Dismiss report"}
        </button>
      </div>
    </div>
  );
}

export default function ReportQueuePage() {
  const { data: queue, isLoading, isError, refetch } = useReviewQueue();
  const [selectedSongId, setSelectedSongId] = React.useState<number | null>(
    null,
  );

  const items = queue ?? [];
  const selectedItem =
    items.find((item) => item.songId === selectedSongId) ?? items[0] ?? null;

  return (
    <div className="flex-1 min-w-0 box-border bg-dotted flex flex-col px-14 pt-11 pb-10">
      <div className="flex items-center gap-[10px] mb-1">
        <h1 className="font-display text-3xl text-accent [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
          Report review queue
        </h1>
        <span className="inline-flex items-center gap-[5px] bg-destructive/15 text-destructive font-display text-[10px] px-[11px] py-[5px] rounded-full">
          ADMIN
        </span>
      </div>
      <p className="text-[13px] text-muted-foreground mb-6">
        Ranked by priority, not submission time. Every resolution is a manual
        call, nothing here auto-applies a suggested year.
      </p>

      {isLoading ? (
        <div className="flex gap-6 flex-1 min-h-0" data-testid="report-queue-skeleton">
          <Skeleton className="w-[500px] shrink-0 rounded-xl" />
          <Skeleton className="flex-1 min-w-0 rounded-xl" />
        </div>
      ) : isError ? (
        <div className="text-destructive">Failed to load the review queue.</div>
      ) : items.length === 0 ? (
        <div className="text-muted-foreground">
          The review queue is empty. Nothing needs attention right now.
        </div>
      ) : (
        <div className="flex gap-6 flex-1 min-h-0 animate-in fade-in-0 duration-200">
          <div className="w-[500px] shrink-0 bg-card border-[3px] border-border-strong rounded-xl shadow-lg box-border overflow-hidden flex flex-col">
            <div className="flex-1 min-h-0 overflow-y-auto">
              {items.map((item) => (
                <QueueRow
                  key={item.songId}
                  item={item}
                  selected={selectedItem?.songId === item.songId}
                  onSelect={() => setSelectedSongId(item.songId ?? null)}
                />
              ))}
            </div>
          </div>

          {selectedItem ? (
            <ReportDetail
              item={selectedItem}
              onResolved={() => {
                setSelectedSongId(null);
                void refetch();
              }}
            />
          ) : null}
        </div>
      )}
    </div>
  );
}
