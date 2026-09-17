"use client";

import React from "react";

import {
  useBacklogStatus,
  useEnqueue,
} from "@/hooks/generated/admin-catalog-seeding/admin-catalog-seeding";
import type { EnqueueResultDTO } from "@/hooks/models/enqueueResultDTO";

const YOUTUBE_ID_INPUT_PLACEHOLDER = [
  "https://youtube.com/playlist?list=...",
  "dQw4w9WgXcQ",
  "jNQXAC9IVRw",
].join("\n");

function looksLikePlaylistLink(line: string): boolean {
  return line.includes("youtube.com") || line.includes("youtu.be");
}

function parseSeedingInput(raw: string): {
  playlistLink?: string;
  youtubeIds: string[];
} {
  const lines = raw
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  const playlistLink = lines.find(looksLikePlaylistLink);
  const youtubeIds = lines.filter((line) => !looksLikePlaylistLink(line));

  return { playlistLink, youtubeIds };
}

function StatTile({
  label,
  value,
  suffix,
  valueClassName,
}: {
  label: string;
  value: React.ReactNode;
  suffix?: React.ReactNode;
  valueClassName: string;
}) {
  return (
    <div className="flex-1 bg-card border-[3px] border-border-strong rounded-xl shadow-lg box-border px-6 py-5">
      <div className="font-sans font-semibold text-[11px] tracking-[0.5px] uppercase text-muted-foreground mb-2">
        {label}
      </div>
      <div className={`font-display text-[34px] ${valueClassName}`}>
        {value}
        {suffix ? (
          <span className="text-base text-muted-foreground"> {suffix}</span>
        ) : null}
      </div>
    </div>
  );
}

function EnqueueSummary({ result }: { result: EnqueueResultDTO }) {
  const enqueued = result.enqueuedYoutubeIds?.length ?? 0;
  const skippedKnown = result.skippedAlreadyKnownYoutubeIds?.length ?? 0;
  const skippedQueued = result.skippedAlreadyQueuedYoutubeIds?.length ?? 0;
  return (
    <div className="mt-4 text-[12px] text-muted-foreground leading-[1.6]">
      Enqueued <span className="font-bold text-primary">{enqueued}</span>, skipped{" "}
      <span className="font-bold text-card-foreground">{skippedKnown}</span> already
      known and <span className="font-bold text-card-foreground">{skippedQueued}</span>{" "}
      already queued.
    </div>
  );
}

export default function CatalogBacklogPage() {
  const {
    data: status,
    isLoading: statusLoading,
    isError: statusError,
    refetch: refetchStatus,
  } = useBacklogStatus();
  const enqueueMutation = useEnqueue();

  const [seedingInput, setSeedingInput] = React.useState("");

  function handleEnqueue() {
    const { playlistLink, youtubeIds } = parseSeedingInput(seedingInput);
    if (!playlistLink && youtubeIds.length === 0) {
      return;
    }
    enqueueMutation.mutate(
      { data: { playlistLink, youtubeIds } },
      {
        onSuccess: () => {
          setSeedingInput("");
          void refetchStatus();
        },
      },
    );
  }

  const pending = status?.pendingCount ?? 0;
  const processedToday = status?.processedTodayCount ?? 0;
  const quotaRemaining = status?.quotaRemainingToday ?? 0;
  const dailyQuota = status?.dailyDrainQuota ?? 0;

  return (
    <div className="flex-1 min-w-0 box-border bg-dotted flex flex-col px-14 pt-11 pb-10">
      <div className="flex items-center gap-[10px] mb-1">
        <h1 className="font-display text-3xl text-accent [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
          Catalog backlog
        </h1>
        <span className="inline-flex items-center gap-[5px] bg-destructive/15 text-destructive font-display text-[10px] px-[11px] py-[5px] rounded-full">
          ADMIN
        </span>
      </div>
      <p className="text-[13px] text-muted-foreground mb-[26px]">
        Grows the catalog proactively. Patient by design, a multi-day drain is
        fine.
      </p>

      <div className="flex gap-5 mb-[26px] shrink-0">
        {statusError ? (
          <div className="flex-1 text-destructive">
            Failed to load backlog status.
          </div>
        ) : (
          <>
            <StatTile
              label="Pending"
              value={statusLoading ? "..." : pending.toLocaleString()}
              valueClassName="text-warning"
            />
            <StatTile
              label="Processed today"
              value={statusLoading ? "..." : processedToday.toLocaleString()}
              valueClassName="text-primary"
            />
            <StatTile
              label="Daily quota remaining"
              value={statusLoading ? "..." : quotaRemaining.toLocaleString()}
              suffix={statusLoading ? undefined : `/ ${dailyQuota.toLocaleString()}`}
              valueClassName="text-accent"
            />
          </>
        )}
      </div>

      <div className="flex gap-6 flex-1 min-h-0">
        <div className="w-[420px] shrink-0 bg-card border-[3px] border-border-strong rounded-xl shadow-lg box-border p-[22px] flex flex-col">
          <div className="font-display text-sm text-card-foreground mb-[14px]">
            Add to backlog
          </div>
          <div className="mb-4">
            <label
              htmlFor="seeding-input"
              className="block mb-1.5 text-[11px] font-semibold uppercase tracking-[0.5px] text-muted-foreground"
            >
              YouTube playlist link, or one video ID per line
            </label>
            <textarea
              id="seeding-input"
              rows={9}
              value={seedingInput}
              onChange={(event) => setSeedingInput(event.target.value)}
              placeholder={YOUTUBE_ID_INPUT_PLACEHOLDER}
              className="w-full box-border bg-background border-2 border-secondary rounded-md px-4 py-[13px] font-mono text-[13px] text-card-foreground resize-none"
            />
          </div>
          <div className="text-[12px] text-muted-foreground leading-[1.6] mb-[18px]">
            Already-known songs are filtered out automatically before anything is
            enqueued, only genuinely new IDs join the backlog.
          </div>
          <button
            type="button"
            onClick={handleEnqueue}
            disabled={enqueueMutation.isPending || seedingInput.trim().length === 0}
            className="w-full justify-center font-display text-[13px] text-primary-foreground bg-primary px-6 py-3 rounded-full shadow-sm box-border cursor-pointer inline-flex items-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="3"
              strokeLinecap="round"
              aria-hidden="true"
            >
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            {enqueueMutation.isPending ? "Adding..." : "Add to backlog"}
          </button>
          {enqueueMutation.isError ? (
            <div className="mt-4 text-[12px] text-destructive">
              Enqueue failed. Check the link or IDs and try again.
            </div>
          ) : enqueueMutation.data ? (
            <EnqueueSummary result={enqueueMutation.data} />
          ) : null}
        </div>

        <div className="flex-1 min-w-0 bg-card border-[3px] border-border-strong rounded-xl shadow-lg box-border overflow-hidden flex flex-col">
          <div className="px-5 pt-[18px] pb-[14px] font-display text-sm text-card-foreground border-b-2 border-background flex items-center justify-between">
            Processing queue
            <span className="font-sans font-semibold text-[11px] text-muted-foreground">
              {statusLoading ? "" : `${pending.toLocaleString()} waiting`}
            </span>
          </div>
          <div className="flex-1 min-h-0 overflow-y-auto flex items-center justify-center p-6 text-center">
            <p className="text-[12px] text-muted-foreground leading-[1.6] max-w-[360px]">
              A per-item processing view is not exposed by the backend yet, the
              seeding API reports aggregate counts only. The pending total above
              reflects the live backlog depth.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
