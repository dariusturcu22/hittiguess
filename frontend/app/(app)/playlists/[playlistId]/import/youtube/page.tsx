"use client";

import React, { use } from "react";
import Link from "next/link";
import { AlertTriangle, Check, Clock3, LoaderCircle, Music, Search, Video } from "lucide-react";

import { useExpandPlaylist } from "@/hooks/generated/bulk-import/bulk-import";
import { useActiveImport, useImportJob, useStartImport } from "@/hooks/generated/playlist-import-jobs/playlist-import-jobs";
import { useGetPlaylist } from "@/hooks/generated/playlist-management/playlist-management";
import type { PlaylistImportJobItemDTO } from "@/hooks/models/playlistImportJobItemDTO";
import { useRouter } from "next/navigation";
import type { AxiosError } from "axios";
import { toast } from "sonner";
import { useBulkImportRealtime } from "@/hooks/use-bulk-import-realtime";
import { countImportItems, loadActiveImportJob, saveActiveImportJob } from "@/lib/playlist-import-job";
import { playlistTitleColor } from "@/lib/playlist-colors";

interface PageProps {
  params: Promise<{ playlistId: string }>;
}

const YOUTUBE_INPUT_PLACEHOLDER = "https://youtube.com/playlist?list=...";
// Every API call counts toward the core service's per-user limit of 60 a minute, and
// the sidebar polls the same import alongside this page, so this stays at five seconds.
const ACTIVE_IMPORT_REFRESH_MILLISECONDS = 5_000;
const WORKING_STAGE_LABELS: Record<string, string> = {
  IDENTIFYING: "Identifying the song...",
  DATING: "Finding the year...",
};
const NOT_FOUND_STATUS = 404;
const BAD_REQUEST_STATUS = 400;
const TOO_MANY_REQUESTS_STATUS = 429;
const IMPORT_START_FAILED_MESSAGE = "Import failed to start. Check the link and try again.";

function isJobGoneError(error: unknown): boolean {
  if (typeof error === "object" && error !== null && "response" in error) {
    const response = (error as { response?: { status?: number } }).response;
    return response?.status === NOT_FOUND_STATUS;
  }
  return false;
}

// The size cap and the daily new-song limit come back as 400 and 429 with a message
// saying what to do, so those are shown as they are.
function importStartErrorMessage(error: unknown): string {
  const response = (error as AxiosError<{ message?: string }>).response;
  const isExplainedRefusal = response?.status === BAD_REQUEST_STATUS || response?.status === TOO_MANY_REQUESTS_STATUS;
  return isExplainedRefusal && response?.data?.message ? response.data.message : IMPORT_START_FAILED_MESSAGE;
}

function ImportItemRow({ item }: { item: PlaylistImportJobItemDTO }) {
  const isResolved = item.status === "RESOLVED" || item.status === "ALREADY_KNOWN";
  const isUnmatched = item.status === "UNRESOLVED";

  if (isResolved) {
    return (
      <div className="flex items-center gap-3.5 border-b-2 border-background px-5 py-3 last:border-b-0">
        <span
          className="flex size-10 shrink-0 items-center justify-center rounded-[10px]"
          style={{ backgroundColor: playlistTitleColor(item.resolvedColor) }}
        >
          <Music className="size-[18px]" stroke="rgba(17,17,27,0.5)" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm text-card-foreground">{item.resolvedTitle}</span>
          <span className="mt-0.5 block truncate text-xs text-muted-foreground">{item.resolvedArtists}</span>
        </span>
        <span
          className="w-[50px] shrink-0 text-right font-display text-[15px]"
          style={{ color: playlistTitleColor(item.resolvedColor) }}
        >
          {item.resolvedReleaseYear}
        </span>
        <Check className="size-[17px] shrink-0 text-green" strokeWidth={2.5} />
      </div>
    );
  }

  if (isUnmatched) {
    return (
      <div className="flex items-center gap-3.5 border-b-2 border-background px-5 py-3 last:border-b-0">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-[10px] border-2 border-dashed border-secondary bg-background">
          <AlertTriangle className="size-[18px] text-destructive" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm italic text-muted-foreground">
            {item.rawTitle ?? item.youtubeId}
          </span>
          {item.rawChannelTitle ? (
            <span className="mt-0.5 block truncate text-xs text-muted-foreground/70">
              Uploaded by {item.rawChannelTitle}
            </span>
          ) : null}
        </span>
        <span className="flex shrink-0 items-center gap-1.5 text-[12px] font-semibold text-destructive">
          No match found
        </span>
      </div>
    );
  }

  const workingStageLabel = item.status ? WORKING_STAGE_LABELS[item.status] : undefined;
  const isWorking = workingStageLabel !== undefined;

  return (
    <div className={`flex items-center gap-3.5 border-b-2 border-background px-5 py-3 last:border-b-0 ${isWorking ? "bg-primary/5" : ""}`}>
      <span className={`flex size-10 shrink-0 items-center justify-center rounded-[10px] border-2 border-dashed bg-background ${isWorking ? "border-primary" : "border-secondary"}`}>
        {isWorking
          ? <LoaderCircle className="size-[18px] animate-spin text-primary" />
          : <Clock3 className="size-[18px] text-muted-foreground/60" />}
      </span>
      <span className="min-w-0 flex-1">
        <span className={`block truncate text-sm italic ${isWorking ? "text-card-foreground" : "text-muted-foreground/70"}`}>
          {item.rawTitle ?? item.youtubeId}
        </span>
        {item.rawChannelTitle ? (
          <span className="mt-0.5 block truncate text-xs text-muted-foreground/70">
            Uploaded by {item.rawChannelTitle}
          </span>
        ) : null}
      </span>
      <span className={`flex shrink-0 items-center gap-1.5 whitespace-nowrap text-xs ${isWorking ? "font-semibold text-primary" : "text-muted-foreground/70"}`}>
        {workingStageLabel ?? "Waiting"}
      </span>
    </div>
  );
}

function CountChip({ label, count, className }: { label: string; count: number; className: string }) {
  return <span className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${className}`}>{count} {label}</span>;
}

export default function ImportYoutubePage({ params }: PageProps) {
  const { playlistId: rawId } = use(params);
  const playlistId = parseInt(rawId);
  const router = useRouter();
  const playlistQuery = useGetPlaylist(playlistId);
  const expandMutation = useExpandPlaylist();
  const startImportMutation = useStartImport();
  const { reset } = useBulkImportRealtime({ subscribeToProgress: false });

  const [playlistLink, setPlaylistLink] = React.useState("");
  const [expandedVideoIds, setExpandedVideoIds] = React.useState<string[] | null>(null);
  const [importStartError, setImportStartError] = React.useState("");
  const [importJobId, setImportJobId] = React.useState<string | undefined>(() => {
    const storedJob = loadActiveImportJob();
    return storedJob !== null && storedJob.playlistId === playlistId ? storedJob.importJobId : undefined;
  });
  const [isImportStarted, setIsImportStarted] = React.useState(() => importJobId !== undefined);
  const activeImportQuery = useActiveImport(playlistId, {
    query: {
      retry: false,
      refetchInterval: ACTIVE_IMPORT_REFRESH_MILLISECONDS,
      enabled: isImportStarted,
    },
  });
  const isJobGone = activeImportQuery.isError && isJobGoneError(activeImportQuery.error);
  // Once the job finishes it stops being the active import, often between two polls
  // now that songs resolve in parallel, so its final results are read by id.
  const finishedImportQuery = useImportJob(playlistId, importJobId ?? "", {
    query: { enabled: isJobGone && importJobId !== undefined, retry: false },
  });
  const importItems = isJobGone && finishedImportQuery.data
    ? (finishedImportQuery.data.items ?? [])
    : activeImportQuery.data
      ? (activeImportQuery.data.items ?? [])
      : [];
  const importCounts = countImportItems(importItems);
  const processedImportCount = importCounts.settled;
  const addedImportCount = importCounts.added;
  const isConnectionStale = activeImportQuery.isError && !isJobGone;
  const isImportFinished = isImportStarted
    && (isJobGone || (importItems.length > 0 && processedImportCount === importItems.length));

  function handleExpand() {
    const trimmedLink = playlistLink.trim();
    if (!trimmedLink) {
      return;
    }
    setExpandedVideoIds(null);
    expandMutation.mutate(
      { data: { playlistLink: trimmedLink } },
      { onSuccess: (videoIds) => setExpandedVideoIds(videoIds) },
    );
  }

  function handleImport() {
    if (!expandedVideoIds || expandedVideoIds.length === 0) {
      return;
    }
    reset();
    setImportStartError("");
    startImportMutation.mutate(
      { playlistId, data: { videoIdsOrLinks: expandedVideoIds } },
      {
        onSuccess: (response) => {
          const startedJobId = response.importJobId;
          if (startedJobId) {
            saveActiveImportJob({ importJobId: startedJobId, playlistId });
            setImportJobId(startedJobId);
          }
          setIsImportStarted(true);
        },
        onError: (error) => {
          const message = importStartErrorMessage(error);
          setImportStartError(message);
          toast.error(message);
        },
      },
    );
  }

  return (
    <div className="flex-1 flex items-center justify-center px-6 py-12">
      <div className="w-[560px] max-w-full bg-card border-[3px] border-border-strong rounded-2xl shadow-lg box-border px-12 pt-11 pb-12 flex flex-col">
        {isImportStarted ? (
          <>
            <h1 className="font-display text-2xl text-destructive mb-1.5 [text-shadow:3px_3px_0_var(--text-shadow-on-card)]">
              Importing playlist
            </h1>
            <p className="text-[13px] text-muted-foreground mb-6">
              {playlistQuery.data?.name ?? "This playlist"}
              {playlistLink ? <>&nbsp;&bull;&nbsp;{playlistLink}</> : null}
            </p>
          </>
        ) : (
          <>
            <div className="mx-auto mb-6 flex size-[84px] items-center justify-center rounded-2xl bg-destructive/15 text-destructive">
              <Video className="size-8" />
            </div>
            <h1 className="font-display text-2xl text-destructive text-center mb-2 [text-shadow:3px_3px_0_var(--text-shadow-on-card)]">
              Import from YouTube
            </h1>
            <p className="text-[13px] text-muted-foreground text-center mb-7 leading-[1.5]">
              Paste a playlist link. Every song is looked up at once, and each one lands in the playlist as soon as it&apos;s ready.
            </p>
          </>
        )}

        {isImportStarted ? (
          <div className="mt-1">
            <div className="flex items-center gap-3">
              <div className="h-2 flex-1 overflow-hidden rounded-full border-2 border-border-strong bg-secondary">
                <div
                  className="h-full rounded-full bg-green transition-all"
                  style={{ width: `${importItems.length === 0 ? 0 : (processedImportCount / importItems.length) * 100}%` }}
                />
              </div>
              <span className="shrink-0 whitespace-nowrap text-xs text-muted-foreground">
                {isImportFinished
                  ? `${addedImportCount} of ${importItems.length} added`
                  : importItems.length === 0
                    ? "Starting..."
                    : `${processedImportCount} of ${importItems.length} processed`}
              </span>
            </div>
            {importItems.length > 0 ? (
              <div className="mt-3 flex flex-wrap gap-2" aria-label="Import progress by stage">
                <CountChip label="working" count={importCounts.working} className="bg-primary/15 text-primary" />
                <CountChip label="waiting" count={importCounts.waiting} className="bg-secondary text-muted-foreground" />
                <CountChip label="added" count={importCounts.added} className="bg-green/15 text-green" />
                {importCounts.unmatched > 0 ? <CountChip label="no match" count={importCounts.unmatched} className="bg-destructive/15 text-destructive" /> : null}
              </div>
            ) : null}
            {isConnectionStale && !isImportFinished ? (
              <p className="mt-2 text-[12px] text-warning">Connection hiccup. Retrying...</p>
            ) : null}
            {importItems.length > 0 ? (
              <div className="mt-4 max-h-[420px] overflow-y-auto rounded-2xl border-[3px] border-border-strong bg-card">
                {importItems.map((item) => (
                  <ImportItemRow key={item.youtubeId ?? ""} item={item} />
                ))}
              </div>
            ) : null}
            {isImportFinished ? (
              <button
                type="button"
                onClick={() => router.push(`/playlists/${playlistId}`)}
                className="mt-4 w-full rounded-full bg-primary px-4 py-2.5 font-display text-xs text-primary-foreground"
              >
                View playlist
              </button>
            ) : (
              <p className="mt-4 text-[12px] leading-relaxed text-muted-foreground">
                You can close this page. The import keeps running in the background.
              </p>
            )}
          </div>
        ) : (
          <>
        <label
          htmlFor="youtube-input"
          className="block mb-1.5 text-[11px] font-semibold uppercase tracking-[0.5px] text-muted-foreground"
        >
          YouTube playlist link
        </label>
        <input
          id="youtube-input"
          type="url"
          value={playlistLink}
          onChange={(event) => setPlaylistLink(event.target.value)}
          placeholder={YOUTUBE_INPUT_PLACEHOLDER}
          className="w-full box-border bg-background border-2 border-secondary rounded-md px-4 py-[13px] font-sans text-[13px] text-card-foreground mb-5"
        />

        <button
          type="button"
          onClick={handleExpand}
          disabled={expandMutation.isPending || playlistLink.trim().length === 0}
          className="w-full font-display text-sm text-primary-foreground bg-primary py-[15px] rounded-full shadow-sm box-border cursor-pointer text-center disabled:opacity-60 disabled:cursor-not-allowed"
        >
          <Search className="mr-2 inline size-4" />
          {expandMutation.isPending ? "Fetching..." : "Fetch playlist"}
        </button>

        {expandMutation.isPending ? (
          <div className="mt-6 rounded-xl bg-background p-5">
            <div className="flex items-center gap-3 text-[13px] font-semibold text-card-foreground">
              <LoaderCircle className="size-4 animate-spin text-primary" />
              Reading playlist data
            </div>
          </div>
        ) : expandMutation.isError ? (
          <p className="mt-4 text-[12px] text-destructive text-center">
            Couldn&apos;t read that link. Check it and try again.
          </p>
        ) : expandedVideoIds ? (
          <div className="mt-6 rounded-xl bg-background p-5">
            <p className="text-[13px] font-semibold text-card-foreground">
              {expandedVideoIds.length} song{expandedVideoIds.length === 1 ? "" : "s"} found. Import them?
            </p>
            <div className="mt-3 grid max-h-56 grid-cols-4 gap-2 overflow-y-auto pr-1">
              {expandedVideoIds.map((videoId) => (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  key={videoId}
                  src={`https://i.ytimg.com/vi/${videoId}/default.jpg`}
                  alt=""
                  loading="lazy"
                  className="aspect-video w-full rounded-lg object-cover"
                />
              ))}
            </div>
            <div className="mt-4 flex gap-2.5">
              <button
                type="button"
                onClick={handleImport}
                disabled={startImportMutation.isPending || expandedVideoIds.length === 0}
                className="flex-1 rounded-full bg-primary px-4 py-2.5 font-display text-xs text-primary-foreground disabled:opacity-60"
              >
                {startImportMutation.isPending ? "Starting..." : `Import ${expandedVideoIds.length} songs`}
              </button>
              <button
                type="button"
                onClick={() => setExpandedVideoIds(null)}
                disabled={startImportMutation.isPending}
                className="rounded-full border-2 border-border px-4 py-2.5 text-xs font-semibold text-card-foreground disabled:opacity-60"
              >
                Back
              </button>
            </div>
          </div>
        ) : null}

        {importStartError && !isImportStarted ? (
          <p className="mt-4 text-[12px] text-destructive text-center">
            {importStartError}
          </p>
        ) : null}
          </>
        )}

        <Link
          href={isImportStarted ? `/playlists/${playlistId}` : `/playlists/${playlistId}/import`}
          className="text-[12px] text-muted-foreground mt-6 text-center cursor-pointer hover:text-card-foreground"
        >
          {isImportStarted ? "Back to playlist" : "‹ Back"}
        </Link>
      </div>
    </div>
  );
}
