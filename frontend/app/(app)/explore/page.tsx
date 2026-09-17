"use client";

import React from "react";

import {
  useGetPublicPlaylists,
  useSavePlaylist,
} from "@/hooks/generated/playlist-management/playlist-management";
import type { PublicPlaylistSummaryDTO } from "@/hooks/models/publicPlaylistSummaryDTO";
import { PhantomEmptyState } from "@/components/phantom-empty-state";

function PlayIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="rgba(255,255,255,0.55)"
      aria-hidden="true"
    >
      <path d="M8 5v14l11-7z" />
    </svg>
  );
}

function PlaylistCard({ playlist }: { playlist: PublicPlaylistSummaryDTO }) {
  const saveMutation = useSavePlaylist();
  const saved = saveMutation.isSuccess;

  return (
    <div className="bg-card border-[3px] border-border-strong rounded-xl shadow-lg overflow-hidden flex flex-col">
      {/* The public-playlist DTO carries a single cover colour, not per-song
          artwork, so the cover is one tinted panel rather than the mockup's
          decorative four-tile mosaic. */}
      <div
        className="aspect-square flex items-center justify-center"
        style={{ background: `#${playlist.color}` }}
      >
        <PlayIcon />
      </div>
      <div className="px-[18px] pt-4 pb-[18px] flex flex-col gap-1">
        <div className="font-display text-base text-card-foreground truncate">
          {playlist.name}
        </div>
        <div className="text-[12px] text-muted-foreground">
          {playlist.songCount} {playlist.songCount === 1 ? "song" : "songs"}
        </div>
        <div className="text-[11px] text-muted-foreground/70 mb-2.5">
          by {playlist.owner?.username ?? "unknown"}
        </div>
        <button
          type="button"
          onClick={() => saveMutation.mutate({ playlistId: playlist.id })}
          disabled={saveMutation.isPending || saved}
          className="w-full text-center font-display text-[12px] text-primary-foreground bg-primary py-2.5 rounded-full shadow-xs box-border cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {saved ? "Saved" : saveMutation.isPending ? "Saving..." : "Save"}
        </button>
      </div>
    </div>
  );
}

export default function ExplorePlaylistsPage() {
  const { data: playlists, isLoading, isError } = useGetPublicPlaylists();
  const [searchTerm, setSearchTerm] = React.useState("");

  const normalizedSearch = searchTerm.trim().toLowerCase();
  const visiblePlaylists = (playlists ?? []).filter((playlist) => {
    if (normalizedSearch.length === 0) {
      return true;
    }
    const haystack = `${playlist.name} ${playlist.owner?.username ?? ""}`.toLowerCase();
    return haystack.includes(normalizedSearch);
  });

  return (
    <div className="flex-1 min-h-0 box-border flex flex-col px-14 pt-11 pb-[50px]">
      <h1 className="font-display text-[34px] text-accent mb-6 [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
        Explore public playlists
      </h1>

      <div className="relative w-[420px] mb-5">
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="text-muted-foreground absolute left-4 top-1/2 -translate-y-1/2"
          aria-hidden="true"
        >
          <circle cx="11" cy="11" r="7" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
        <input
          type="text"
          value={searchTerm}
          onChange={(event) => setSearchTerm(event.target.value)}
          placeholder="Search public playlists..."
          className="w-full box-border bg-background border-2 border-secondary rounded-full py-3 pl-[42px] pr-[18px] font-sans text-sm text-card-foreground"
        />
      </div>

      <div className="flex-1 min-h-0 overflow-y-auto pr-1 flex flex-col">
        {isLoading ? (
          <div className="text-muted-foreground">Loading public playlists...</div>
        ) : isError ? (
          <div className="text-destructive">
            Failed to load public playlists.
          </div>
        ) : visiblePlaylists.length === 0 ? (
          normalizedSearch.length > 0 ? (
            <PhantomEmptyState
              title="Nothing matches"
              message="No public playlists match your search. Try a different name."
            />
          ) : (
            <PhantomEmptyState
              title="No public playlists yet"
              message="When people publish playlists, they will show up here to explore and save."
            />
          )
        ) : (
          <div className="grid grid-cols-4 gap-6">
            {visiblePlaylists.map((playlist) => (
              <PlaylistCard key={playlist.id} playlist={playlist} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
