"use client";

import React, { use } from "react";
import Link from "next/link";
import { Check, LoaderCircle, Search, Video } from "lucide-react";

import { useImportImmediately } from "@/hooks/generated/bulk-import/bulk-import";
import { toast } from "sonner";

interface PageProps {
  params: Promise<{ playlistId: string }>;
}

const YOUTUBE_INPUT_PLACEHOLDER = "https://youtube.com/playlist?list=...";

export default function ImportYoutubePage({ params }: PageProps) {
  const { playlistId: rawId } = use(params);
  const playlistId = parseInt(rawId);
  const importMutation = useImportImmediately();

  const [playlistLink, setPlaylistLink] = React.useState("");

  function handleImport() {
    const trimmedLink = playlistLink.trim();
    if (!trimmedLink) {
      return;
    }
    const toastId = toast.loading("Importing playlist...");
    window.dispatchEvent(new CustomEvent("playlist-import-progress", { detail: true }));
    importMutation.mutate(
      { data: { playlistLink: trimmedLink, targetPlaylistId: playlistId } },
      {
        onSuccess: () => toast.success("Playlist import complete.", { id: toastId }),
        onError: () => toast.error("Playlist import failed.", { id: toastId }),
        onSettled: () => window.dispatchEvent(new CustomEvent("playlist-import-progress", { detail: false })),
      },
    );
  }

  const result = importMutation.data;

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
          onClick={handleImport}
          disabled={importMutation.isPending || playlistLink.trim().length === 0}
          className="w-full font-display text-sm text-primary-foreground bg-primary py-[15px] rounded-full shadow-sm box-border cursor-pointer text-center disabled:opacity-60 disabled:cursor-not-allowed"
        >
          <Search className="mr-2 inline size-4" />
          {importMutation.isPending ? "Fetching..." : "Fetch playlist"}
        </button>

        {importMutation.isPending ? (
          <div className="mt-6 rounded-xl bg-background p-5">
            <div className="flex items-center gap-3 text-[13px] font-semibold text-card-foreground">
              <LoaderCircle className="size-4 animate-spin text-primary" />
              Reading playlist and matching songs
            </div>
            <div className="mt-4 h-2 overflow-hidden rounded-full bg-secondary">
              <div className="h-full w-2/3 animate-pulse rounded-full bg-primary" />
            </div>
            <p className="mt-3 text-[11px] text-muted-foreground">
              Keep this page open while the playlist is processed.
            </p>
          </div>
        ) : importMutation.isError ? (
          <p className="mt-4 text-[12px] text-destructive text-center">
            Import failed. Check the link or IDs and try again.
          </p>
        ) : result ? (
          <div className="mt-6 rounded-xl bg-background px-5 py-4 text-[12px] text-muted-foreground">
            <div className="flex items-center gap-3 font-semibold text-card-foreground">
              <span className="flex size-7 items-center justify-center rounded-full bg-primary text-primary-foreground">
                <Check className="size-4" />
              </span>
              Import complete
            </div>
            <div className="mt-4 grid grid-cols-3 gap-2 text-center">
              <div className="rounded-lg bg-card p-3"><strong className="block font-display text-lg text-primary">{result.resolvedYoutubeIds?.length ?? 0}</strong>Added</div>
              <div className="rounded-lg bg-card p-3"><strong className="block font-display text-lg text-card-foreground">{result.alreadyKnownYoutubeIds?.length ?? 0}</strong>Already known</div>
              <div className="rounded-lg bg-card p-3"><strong className="block font-display text-lg text-destructive">{result.unresolvedYoutubeIds?.length ?? 0}</strong>Needs review</div>
            </div>
            <Link href={`/playlists/${playlistId}`} className="mt-4 block text-center font-display text-[11px] text-primary">
              View playlist
            </Link>
          </div>
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
