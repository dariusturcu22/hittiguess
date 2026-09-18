"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/shadcn/button";
import { PlaylistSummaryDTO } from "@/hooks/models";
import {
  useCreatePlaylist,
  getGetUserPlaylistsQueryKey,
  useGetUserPlaylists,
} from "@/hooks/generated/user-management/user-management";
import { getPlaylistMosaicColors } from "@/lib/playlist-mosaic-colors";

function PlusIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="3"
      strokeLinecap="round"
    >
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </svg>
  );
}

function SearchIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="pointer-events-none absolute top-1/2 left-4 -translate-y-1/2 text-muted-foreground"
    >
      <circle cx="11" cy="11" r="7" />
      <line x1="21" y1="21" x2="16.65" y2="16.65" />
    </svg>
  );
}

function PlayTileIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="rgba(255,255,255,0.6)">
      <path d="M8 5v14l11-7z" />
    </svg>
  );
}

function PlaylistCard({ playlist }: { playlist: PlaylistSummaryDTO }) {
  const tileColors = getPlaylistMosaicColors(playlist.color);

  return (
    <Link
      href={`/playlists/${playlist.id}`}
      className="flex flex-col overflow-hidden rounded-2xl border-[3px] border-border-strong bg-card shadow-lg"
    >
      <div className="grid aspect-square grid-cols-2 grid-rows-2 gap-0.5 bg-border-strong">
        {tileColors.map((color, index) => (
          <div
            key={index}
            className="flex items-center justify-center"
            style={{ backgroundColor: color }}
          >
            <PlayTileIcon />
          </div>
        ))}
      </div>
      <div className="flex flex-col gap-1.5 p-4 pb-[18px]">
        <div className="font-display text-base text-card-foreground">
          {playlist.name}
        </div>
        <div className="text-xs text-muted-foreground">
          {playlist.songCount} songs
        </div>
      </div>
    </Link>
  );
}

function NewPlaylistCard({
  onClick,
  isCreating,
}: {
  onClick: () => void;
  isCreating: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={isCreating}
      className="flex aspect-square cursor-pointer flex-col items-center justify-center gap-3 self-start rounded-2xl border-[3px] border-dashed border-border disabled:cursor-not-allowed disabled:opacity-50"
    >
      <div className="flex h-11 w-11 items-center justify-center rounded-full border-2 border-border">
        <PlusIcon className="text-muted-foreground" />
      </div>
      <div className="font-display text-sm text-muted-foreground">
        {isCreating ? "Creating..." : "New playlist"}
      </div>
    </button>
  );
}

export default function PlaylistsPage() {
  const { data: playlists, isLoading, isError } = useGetUserPlaylists();
  const queryClient = useQueryClient();
  const router = useRouter();

  const { mutate: createPlaylist, isPending: isCreating } =
    useCreatePlaylist();
  const [searchQuery, setSearchQuery] = React.useState("");

  const handleCreatePlaylist = () => {
    createPlaylist(undefined, {
      onSuccess: (newPlaylist) => {
        queryClient.invalidateQueries({
          queryKey: getGetUserPlaylistsQueryKey(),
        });
        router.push(`/playlists/${newPlaylist.id}`);
      },
    });
  };

  const visiblePlaylists = (playlists ?? []).filter((playlist) =>
    playlist.name.toLowerCase().includes(searchQuery.trim().toLowerCase()),
  );

  return (
    <div className="flex h-full flex-col p-6 md:p-11">
      <div className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <h1
          className="font-display text-[26px] text-accent sm:text-[34px]"
          style={{ textShadow: "3px 3px 0 var(--text-shadow-on-page)" }}
        >
          Your playlists
        </h1>

        <div className="flex flex-wrap items-center gap-3">
          <Button
            type="button"
            onClick={handleCreatePlaylist}
            disabled={isCreating}
          >
            <PlusIcon />
            {isCreating ? "Creating..." : "Create playlist"}
          </Button>
        </div>
      </div>

      <div className="mb-7 flex flex-wrap items-center gap-4">
        <div className="relative w-full max-w-[360px]">
          <SearchIcon />
          <input
            type="text"
            placeholder="Search your playlists..."
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
            className="bg-surface-sunken border-border w-full rounded-full border-2 py-3 pr-[18px] pl-[42px] font-sans text-sm text-foreground placeholder:text-muted-foreground outline-none"
          />
        </div>
        {/* The list endpoint doesn't distinguish owned vs. joined playlists,
            so these pills reflect the mockup visually but don't filter. */}
        <span
          className="cursor-pointer rounded-full bg-accent px-6 py-2.5 font-display text-xs text-accent-foreground shadow-xs"
          title="Filtering by ownership isn't available yet"
        >
          Owned
        </span>
        <span
          className="text-muted-foreground border-border cursor-pointer rounded-full border-2 px-6 py-2.5 font-display text-xs"
          title="Filtering by ownership isn't available yet"
        >
          Joined
        </span>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto pr-1">
        {isLoading && (
          <p className="text-muted-foreground text-sm">
            Loading playlists...
          </p>
        )}
        {isError && (
          <p className="text-destructive text-sm">
            Failed to load playlists.
          </p>
        )}
        {!isLoading && !isError && (
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {visiblePlaylists.map((playlist) => (
              <PlaylistCard key={playlist.id} playlist={playlist} />
            ))}
            <NewPlaylistCard
              onClick={handleCreatePlaylist}
              isCreating={isCreating}
            />
          </div>
        )}
      </div>
    </div>
  );
}
