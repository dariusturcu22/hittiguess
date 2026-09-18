"use client";

import React, { use } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AlertCircle, ListMusic, LoaderCircle } from "lucide-react";

import {
  useGetPlaylist,
  useImportFromPlaylist,
} from "@/hooks/generated/playlist-management/playlist-management";
import type { SongDTO } from "@/hooks/models/songDTO";

interface PageProps {
  params: Promise<{ playlistId: string; sourceId: string }>;
}

function artistNames(song: SongDTO): string {
  return (song.artists ?? [])
    .map((artist) => artist.name)
    .filter((name): name is string => Boolean(name))
    .join(", ");
}

function ConfirmRow({ song }: { song: SongDTO }) {
  return (
    <div className="flex items-center gap-[14px] px-5 py-3 border-b-2 border-background last:border-b-0">
      <span
        className="w-9 h-9 rounded-[9px] shrink-0 flex items-center justify-center"
        style={{ background: song.color ?? undefined }}
      >
        <ListMusic className="size-4 text-foreground/40" />
      </span>
      <span className="flex-1 min-w-0">
        <span className="block text-sm text-card-foreground truncate">
          {song.title}
        </span>
        <span className="mt-0.5 block text-[12px] text-muted-foreground truncate">
          {artistNames(song)}
        </span>
      </span>
      <span className="font-display text-[13px] text-accent shrink-0">
        {song.releaseYear}
      </span>
    </div>
  );
}

export default function ImportFromPlaylistConfirmPage({ params }: PageProps) {
  const { playlistId: rawDestId, sourceId: rawSourceId } = use(params);
  const destinationPlaylistId = parseInt(rawDestId);
  const sourcePlaylistId = parseInt(rawSourceId);
  const router = useRouter();

  const { data: sourcePlaylist, isLoading, isError } =
    useGetPlaylist(sourcePlaylistId);
  const importMutation = useImportFromPlaylist();

  const songs = sourcePlaylist?.songs ?? [];
  const songCount = sourcePlaylist?.songCount ?? songs.length;

  function handleImport() {
    importMutation.mutate(
      {
        playlistId: destinationPlaylistId,
        data: { sourcePlaylistId },
      },
      {
        onSuccess: () => {
          router.push(`/playlists/${destinationPlaylistId}`);
        },
      },
    );
  }

  return (
    <div className="flex-1 min-h-0 flex justify-center px-14 pt-11 pb-10">
      <div className="w-[640px] max-w-full flex flex-col">
        <h1 className="font-display text-[28px] text-destructive mb-1.5 [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
          Ready to import
        </h1>
        <p className="text-[13px] text-muted-foreground mb-[18px]">
          {songCount} songs from{" "}
          <strong className="text-card-foreground">
            {sourcePlaylist?.name ?? "this playlist"}
          </strong>
          . No fetching needed, they&apos;re already in the database.
        </p>

        <div className="bg-card border-[3px] border-border-strong rounded-2xl shadow-lg box-border overflow-hidden flex-1 min-h-0 flex flex-col">
          <div className="flex-1 min-h-0 overflow-y-auto">
            {isLoading ? (
              <div className="flex min-h-[260px] flex-col items-center justify-center gap-3 p-6 text-center text-muted-foreground">
                <LoaderCircle className="size-6 animate-spin text-primary" />
                Loading songs...
              </div>
            ) : isError ? (
              <div className="flex min-h-[260px] flex-col items-center justify-center gap-3 p-6 text-center">
                <AlertCircle className="size-6 text-destructive" />
                <p className="text-[13px] text-muted-foreground">Failed to load the source playlist.</p>
              </div>
            ) : songs.length === 0 ? (
              <div className="flex min-h-[260px] flex-col items-center justify-center gap-3 p-6 text-center text-muted-foreground">
                <ListMusic className="size-7 text-icon-muted" />
                This playlist has no songs to copy.
              </div>
            ) : (
              songs.map((song) => <ConfirmRow key={song.id} song={song} />)
            )}
          </div>
        </div>

        <div className="flex items-center gap-4 mt-5 shrink-0">
          <button
            type="button"
            onClick={handleImport}
            disabled={importMutation.isPending || songs.length === 0}
            className="flex-1 font-display text-sm text-primary-foreground bg-primary py-[15px] rounded-full shadow-sm box-border cursor-pointer text-center disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {importMutation.isPending
              ? "Adding..."
              : `Add ${songCount} songs to playlist`}
          </button>
          <Link
            href={`/playlists/${destinationPlaylistId}/import/from-playlist`}
            className="text-[12px] text-muted-foreground cursor-pointer whitespace-nowrap hover:text-card-foreground"
          >
            ‹ Back
          </Link>
        </div>
        {importMutation.isError ? (
          <p className="mt-3 text-[12px] text-destructive">
            Import failed. Try again.
          </p>
        ) : null}
      </div>
    </div>
  );
}
