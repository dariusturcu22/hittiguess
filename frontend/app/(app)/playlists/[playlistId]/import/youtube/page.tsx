"use client";

import React, { use } from "react";
import Link from "next/link";
import { LoaderCircle, Search, Video } from "lucide-react";

import { useExpandPlaylist } from "@/hooks/generated/bulk-import/bulk-import";
import { useStartImport } from "@/hooks/generated/playlist-import-jobs/playlist-import-jobs";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { useBulkImportRealtime } from "@/hooks/use-bulk-import-realtime";
import { saveActiveImportJob } from "@/lib/playlist-import-job";

interface PageProps {
  params: Promise<{ playlistId: string }>;
}

const YOUTUBE_INPUT_PLACEHOLDER = "https://youtube.com/playlist?list=...";

export default function ImportYoutubePage({ params }: PageProps) {
  const { playlistId: rawId } = use(params);
  const playlistId = parseInt(rawId);
  const router = useRouter();
  const expandMutation = useExpandPlaylist();
  const startImportMutation = useStartImport();
  const { reset } = useBulkImportRealtime({ subscribeToProgress: false });

  const [playlistLink, setPlaylistLink] = React.useState("");
  const [expandedVideoIds, setExpandedVideoIds] = React.useState<string[] | null>(null);

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
    startImportMutation.mutate(
      { playlistId, data: { videoIdsOrLinks: expandedVideoIds } },
      {
        onSuccess: (response) => {
          const startedJobId = response.importJobId;
          if (startedJobId) {
            saveActiveImportJob({ importJobId: startedJobId, playlistId });
          }
          toast.success("Import started. Songs appear as they resolve.");
          router.push(`/playlists/${playlistId}`);
        },
        onError: () => toast.error("Import failed to start. Check the link and try again."),
      },
    );
  }

  return (
    <div className="flex-1 flex items-center justify-center px-6 py-12">
      <div className="w-[560px] max-w-full bg-card border-[3px] border-border-strong rounded-2xl shadow-lg box-border px-12 pt-11 pb-12 flex flex-col">
        <div className="mx-auto mb-6 flex size-[84px] items-center justify-center rounded-2xl bg-destructive/15 text-destructive">
          <Video className="size-8" />
        </div>
        <h1 className="font-display text-2xl text-destructive text-center mb-2 [text-shadow:3px_3px_0_var(--text-shadow-on-card)]">
          Import from YouTube
        </h1>
        <p className="text-[13px] text-muted-foreground text-center mb-7 leading-[1.5]">
          Paste a playlist link. We&apos;ll list every song and fetch details for each one.
        </p>

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

        {startImportMutation.isPending ? (
          <div className="mt-6 rounded-xl bg-background p-5">
            <div className="flex items-center gap-3 text-[13px] font-semibold text-card-foreground">
              <LoaderCircle className="size-4 animate-spin text-primary" />
              Starting the import
            </div>
          </div>
        ) : startImportMutation.isError ? (
          <p className="mt-4 text-[12px] text-destructive text-center">
            Import failed to start. Check the link and try again.
          </p>
        ) : null}

        <Link
          href={`/playlists/${playlistId}/import`}
          className="text-[12px] text-muted-foreground mt-6 text-center cursor-pointer hover:text-card-foreground"
        >
          ‹ Back
        </Link>
      </div>
    </div>
  );
}
