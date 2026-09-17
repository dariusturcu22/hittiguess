"use client";

import React, { use } from "react";
import Link from "next/link";

import { useImportImmediately } from "@/hooks/generated/bulk-import/bulk-import";

interface PageProps {
  params: Promise<{ playlistId: string }>;
}

const YOUTUBE_INPUT_PLACEHOLDER = [
  "https://youtube.com/playlist?list=...",
  "dQw4w9WgXcQ",
  "jNQXAC9IVRw",
].join("\n");

function looksLikePlaylistLink(line: string): boolean {
  return line.includes("youtube.com") || line.includes("youtu.be");
}

export default function ImportYoutubePage({ params }: PageProps) {
  const { playlistId: rawId } = use(params);
  const playlistId = parseInt(rawId);
  const importMutation = useImportImmediately();

  const [rawInput, setRawInput] = React.useState("");

  function handleImport() {
    const lines = rawInput
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line.length > 0);
    const playlistLink = lines.find(looksLikePlaylistLink);
    const videoIdsOrLinks = lines.filter((line) => !looksLikePlaylistLink(line));
    if (!playlistLink && videoIdsOrLinks.length === 0) {
      return;
    }
    importMutation.mutate({ data: { playlistLink, videoIdsOrLinks } });
  }

  const result = importMutation.data;

  return (
    <div className="flex-1 flex items-center justify-center px-6 py-12">
      <div className="w-[560px] max-w-full bg-card border-[3px] border-border-strong rounded-2xl shadow-lg box-border px-12 pt-11 pb-12 flex flex-col">
        <h1 className="font-display text-2xl text-destructive text-center mb-2 [text-shadow:3px_3px_0_var(--text-shadow-on-card)]">
          Import from YouTube
        </h1>
        <p className="text-[13px] text-muted-foreground text-center mb-7 leading-[1.5]">
          Paste a YouTube playlist link, or one video ID or link per line.
          Already-known songs are skipped.
        </p>

        <label
          htmlFor="youtube-input"
          className="block mb-1.5 text-[11px] font-semibold uppercase tracking-[0.5px] text-muted-foreground"
        >
          YouTube playlist link or video IDs
        </label>
        <textarea
          id="youtube-input"
          rows={8}
          value={rawInput}
          onChange={(event) => setRawInput(event.target.value)}
          placeholder={YOUTUBE_INPUT_PLACEHOLDER}
          className="w-full box-border bg-background border-2 border-secondary rounded-md px-4 py-[13px] font-mono text-[13px] text-card-foreground resize-none mb-5"
        />

        <button
          type="button"
          onClick={handleImport}
          disabled={importMutation.isPending || rawInput.trim().length === 0}
          className="w-full font-display text-sm text-primary-foreground bg-primary py-[15px] rounded-full shadow-sm box-border cursor-pointer text-center disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {importMutation.isPending ? "Importing..." : "Import songs"}
        </button>

        {importMutation.isError ? (
          <p className="mt-4 text-[12px] text-destructive text-center">
            Import failed. Check the link or IDs and try again.
          </p>
        ) : result ? (
          <div className="mt-5 bg-background rounded-md px-5 py-4 text-[12px] text-muted-foreground leading-[1.7]">
            Resolved{" "}
            <span className="font-bold text-primary">
              {result.resolvedYoutubeIds?.length ?? 0}
            </span>
            , skipped{" "}
            <span className="font-bold text-card-foreground">
              {result.alreadyKnownYoutubeIds?.length ?? 0}
            </span>{" "}
            already known
            {result.unresolvedYoutubeIds?.length
              ? `, ${result.unresolvedYoutubeIds.length} could not be resolved`
              : ""}
            .
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
