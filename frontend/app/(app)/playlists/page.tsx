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
  useGetSavedPlaylists,
  useGetUserPlaylists,
} from "@/hooks/generated/user-management/user-management";
import { PlaylistCoverMosaic } from "@/components/playlist-cover-mosaic";
import { playlistTitleColor } from "@/lib/playlist-colors";

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

type PlaylistCardItem = Pick<
  PlaylistSummaryDTO,
  "id" | "name" | "color" | "songCount" | "previewYoutubeIds"
>;

function PlaylistCard({ playlist }: { playlist: PlaylistCardItem }) {
  return (
    <Link
      href={`/playlists/${playlist.id}`}
      className="flex flex-col overflow-hidden rounded-2xl border-[3px] border-border-strong bg-card shadow-lg"
    >
      <PlaylistCoverMosaic
        previewYoutubeIds={playlist.previewYoutubeIds}
        className="w-full rounded-none border-none"
      />
      <div className="flex flex-col gap-1.5 p-4 pb-[18px]">
        <div
          className="font-display text-base"
          style={{ color: playlistTitleColor(playlist.color) }}
        >
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
  const userPlaylistsQuery = useGetUserPlaylists();
  const savedPlaylistsQuery = useGetSavedPlaylists();
  const queryClient = useQueryClient();
  const router = useRouter();

  const { mutate: createPlaylist, isPending: isCreating } =
    useCreatePlaylist();
  const [searchQuery, setSearchQuery] = React.useState("");
  const [libraryTab, setLibraryTab] = React.useState<"owned" | "joined" | "saved">("owned");

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

  const playlists = userPlaylistsQuery.data;
  const isLoading = libraryTab === "saved" ? savedPlaylistsQuery.isLoading : userPlaylistsQuery.isLoading;
  const isError = libraryTab === "saved" ? savedPlaylistsQuery.isError : userPlaylistsQuery.isError;

  const matchesSearch = (name: string) => name.toLowerCase().includes(searchQuery.trim().toLowerCase());
  const membershipPlaylists = playlists ?? [];
  const savedPlaylists: PlaylistCardItem[] = savedPlaylistsQuery.data ?? [];
  const visiblePlaylists: PlaylistCardItem[] = libraryTab === "saved"
    ? savedPlaylists.filter((playlist) => matchesSearch(playlist.name))
    : membershipPlaylists.filter((playlist) => {
        const ownedByCurrentUser = playlist.ownedByCurrentUser;
        return matchesSearch(playlist.name)
          && (libraryTab === "owned" ? ownedByCurrentUser : !ownedByCurrentUser);
      });

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
        <button type="button" onClick={() => setLibraryTab("owned")} className={`cursor-pointer rounded-full px-6 py-2.5 font-display text-xs ${libraryTab === "owned" ? "bg-accent text-accent-foreground shadow-xs" : "text-muted-foreground border-border border-2"}`}>
          Owned
        </button>
        <button type="button" onClick={() => setLibraryTab("joined")} className={`cursor-pointer rounded-full px-6 py-2.5 font-display text-xs ${libraryTab === "joined" ? "bg-accent text-accent-foreground shadow-xs" : "text-muted-foreground border-border border-2"}`}>
          Joined
        </button>
        <button type="button" onClick={() => setLibraryTab("saved")} className={`cursor-pointer rounded-full px-6 py-2.5 font-display text-xs ${libraryTab === "saved" ? "bg-accent text-accent-foreground shadow-xs" : "text-muted-foreground border-border border-2"}`}>
          Saved
        </button>
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
            {libraryTab === "saved" ? null : (
              <NewPlaylistCard
                onClick={handleCreatePlaylist}
                isCreating={isCreating}
              />
            )}
          </div>
        )}
      </div>
    </div>
  );
}
