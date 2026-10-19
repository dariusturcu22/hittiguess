"use client";

import Link from "next/link";
import React from "react";

import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Skeleton } from "@/components/shadcn/skeleton";
import {
  useGetPublicPlaylists,
  useSavePlaylist,
} from "@/hooks/generated/playlist-management/playlist-management";
import {
  getGetSavedPlaylistsQueryKey,
  useGetSavedPlaylists,
} from "@/hooks/generated/user-management/user-management";
import type { PublicPlaylistSummaryDTO } from "@/hooks/models/publicPlaylistSummaryDTO";
import { PhantomEmptyState } from "@/components/phantom-empty-state";
import { PlaylistCoverMosaic } from "@/components/playlist-cover-mosaic";
import { playlistTitleColor } from "@/lib/playlist-colors";

const EXPLORE_TABS = [
  { id: "all", label: "All" },
  { id: "saved", label: "Saved" },
  { id: "not-saved", label: "Not saved" },
] as const;

type ExploreTab = (typeof EXPLORE_TABS)[number]["id"];

const LOADING_SKELETON_CARD_COUNT = 8;

function PlaylistCard({ playlist, saved }: { playlist: PublicPlaylistSummaryDTO; saved: boolean }) {
  const queryClient = useQueryClient();
  const saveMutation = useSavePlaylist({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getGetSavedPlaylistsQueryKey() });
        toast.success("Playlist saved");
      },
      onError: () => toast.error("Couldn't save that playlist. Try again."),
    },
  });
  const isSaved = saved || saveMutation.isSuccess;

  return (
    <div className="bg-card border-[3px] border-border-strong rounded-xl shadow-lg overflow-hidden flex flex-col">
      <Link
        href={`/playlists/${playlist.id}`}
        aria-label={`Open ${playlist.name}`}
        className="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        <PlaylistCoverMosaic
          previewYoutubeIds={playlist.previewYoutubeIds}
          className="w-full rounded-none border-none"
        />
        <div className="px-[18px] pt-4 flex flex-col gap-1">
          <div
            className="font-display text-base truncate"
            style={{ color: playlistTitleColor(playlist.color) }}
          >
            {playlist.name}
          </div>
          <div className="text-[12px] text-muted-foreground">
            {playlist.songCount} {playlist.songCount === 1 ? "song" : "songs"}
          </div>
          <div className="text-[11px] text-muted-foreground/70 mb-2.5">
            by {playlist.owner?.username ?? "unknown"}
          </div>
        </div>
      </Link>
      <div className="px-[18px] pb-[18px]">
        <button
          type="button"
          onClick={() => saveMutation.mutate({ playlistId: playlist.id })}
          disabled={saveMutation.isPending || isSaved}
          className="w-full text-center font-display text-[12px] text-primary-foreground bg-primary py-2.5 rounded-full shadow-xs box-border cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {isSaved ? "Saved" : saveMutation.isPending ? "Saving..." : "Save"}
        </button>
      </div>
    </div>
  );
}

export default function ExplorePlaylistsPage() {
  const { data: playlists, isLoading, isError } = useGetPublicPlaylists();
  const { data: savedPlaylists } = useGetSavedPlaylists();
  const [searchTerm, setSearchTerm] = React.useState("");
  const [exploreTab, setExploreTab] = React.useState<ExploreTab>("all");

  const savedIds = new Set((savedPlaylists ?? []).map((savedPlaylist) => savedPlaylist.id));
  const normalizedSearch = searchTerm.trim().toLowerCase();
  const visiblePlaylists = (playlists ?? []).filter((playlist) => {
    if (exploreTab === "saved" && !savedIds.has(playlist.id)) {
      return false;
    }
    if (exploreTab === "not-saved" && savedIds.has(playlist.id)) {
      return false;
    }
    if (normalizedSearch.length === 0) {
      return true;
    }
    const haystack = `${playlist.name} ${playlist.owner?.username ?? ""}`.toLowerCase();
    return haystack.includes(normalizedSearch);
  });

  return (
    <div className="h-full min-h-0 box-border flex flex-col px-14 pt-11 pb-[50px]">
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

      <div className="flex gap-4 mb-5">
        {EXPLORE_TABS.map((tab) => (
          <button key={tab.id} type="button" onClick={() => setExploreTab(tab.id)} className={`cursor-pointer rounded-full px-6 py-2.5 font-display text-xs ${exploreTab === tab.id ? "bg-accent text-accent-foreground shadow-xs" : "text-muted-foreground border-border border-2"}`}>
            {tab.label}
          </button>
        ))}
      </div>

      <div className="flex-1 min-h-0 overflow-y-auto pr-1 flex flex-col">
        {isLoading ? (
          <div className="grid grid-cols-4 gap-6" data-testid="explore-loading-skeleton">
            {Array.from({ length: LOADING_SKELETON_CARD_COUNT }, (_, index) => (
              <Skeleton key={index} className="h-56 rounded-xl" />
            ))}
          </div>
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
          <div className="grid grid-cols-4 gap-6 animate-in fade-in-0 slide-in-from-top-1 duration-200">
            {visiblePlaylists.map((playlist) => (
              <PlaylistCard key={playlist.id} playlist={playlist} saved={savedIds.has(playlist.id)} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
