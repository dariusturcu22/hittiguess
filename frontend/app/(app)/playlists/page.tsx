"use client";

import * as React from "react";
import Link from "next/link";
import { Compass } from "lucide-react";
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

const LIBRARY_TABS = [
  { id: "all", label: "All" },
  { id: "owned", label: "Owned" },
  { id: "joined", label: "Joined" },
  { id: "saved", label: "Saved" },
] as const;

type LibraryTab = (typeof LIBRARY_TABS)[number]["id"];

function extractInviteCode(value: string): string {
  const trimmed = value.trim();
  const joinMarker = "/join/";
  const markerIndex = trimmed.lastIndexOf(joinMarker);
  const codeOrLink = markerIndex >= 0 ? trimmed.slice(markerIndex + joinMarker.length) : trimmed;
  return codeOrLink.split(/[?#]/)[0];
}

function JoinPlaylistPopup({ onJoin, onClose }: { onJoin: (inviteCode: string) => void; onClose: () => void }) {
  const [inviteValue, setInviteValue] = React.useState("");
  const inviteCode = extractInviteCode(inviteValue);

  return (
    <div className="absolute top-full right-0 z-20 mt-2 flex w-[300px] flex-col gap-3 rounded-2xl border-[3px] border-border-strong bg-card p-5 shadow-lg">
      <input
        type="text"
        value={inviteValue}
        onChange={(event) => setInviteValue(event.target.value)}
        placeholder="Invite code or link"
        aria-label="Invite code or link"
        className="w-full rounded-full border-2 border-border bg-background px-4 py-2 text-sm text-foreground outline-none placeholder:text-muted-foreground"
      />
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onClose}
          className="cursor-pointer rounded-full px-4 py-2 font-display text-xs text-muted-foreground"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={() => onJoin(inviteCode)}
          disabled={!inviteCode}
          className="cursor-pointer rounded-full bg-accent px-6 py-2 font-display text-xs text-accent-foreground disabled:cursor-not-allowed disabled:opacity-50"
        >
          Join playlist
        </button>
      </div>
    </div>
  );
}

function ExploreLinkCard() {
  return (
    <Link
      href="/explore"
      className="flex aspect-square flex-col items-center justify-center gap-3 self-start rounded-2xl border-[3px] border-dashed border-border p-5"
    >
      <span className="flex h-11 w-11 items-center justify-center rounded-full border-2 border-border">
        <Compass className="size-5 text-muted-foreground" />
      </span>
      <div className="font-display text-sm text-muted-foreground">Explore public playlists</div>
      <span className="rounded-full bg-accent px-6 py-2 font-display text-xs text-accent-foreground">Explore</span>
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
  const [libraryTab, setLibraryTab] = React.useState<LibraryTab>("all");
  const [isJoinOpen, setIsJoinOpen] = React.useState(false);

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

  const handleJoinPlaylist = (inviteCode: string) => {
    router.push(`/playlists/join/${inviteCode}`);
  };

  const playlists = userPlaylistsQuery.data;
  const isLoading = userPlaylistsQuery.isLoading || savedPlaylistsQuery.isLoading;
  const isError = userPlaylistsQuery.isError || savedPlaylistsQuery.isError;

  const matchesSearch = (name: string) => name.toLowerCase().includes(searchQuery.trim().toLowerCase());
  const membershipPlaylists = playlists ?? [];
  const savedPlaylists: PlaylistCardItem[] = savedPlaylistsQuery.data ?? [];
  const ownedPlaylists = membershipPlaylists.filter((playlist) => playlist.ownedByCurrentUser);
  const joinedPlaylists = membershipPlaylists.filter((playlist) => !playlist.ownedByCurrentUser);
  const allPlaylists: PlaylistCardItem[] = [...membershipPlaylists];
  for (const savedPlaylist of savedPlaylists) {
    if (!allPlaylists.some((playlist) => playlist.id === savedPlaylist.id)) {
      allPlaylists.push(savedPlaylist);
    }
  }
  const visiblePlaylists: PlaylistCardItem[] = (libraryTab === "saved"
    ? savedPlaylists
    : libraryTab === "all"
      ? allPlaylists
      : libraryTab === "owned"
        ? ownedPlaylists
        : joinedPlaylists
  ).filter((playlist) => matchesSearch(playlist.name));

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
          <div className="relative">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsJoinOpen((currentValue) => !currentValue)}
            >
              Join
            </Button>
            {isJoinOpen ? (
              <JoinPlaylistPopup
                onJoin={(inviteCode) => {
                  setIsJoinOpen(false);
                  handleJoinPlaylist(inviteCode);
                }}
                onClose={() => setIsJoinOpen(false)}
              />
            ) : null}
          </div>
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
        {LIBRARY_TABS.map((tab) => (
          <button key={tab.id} type="button" onClick={() => setLibraryTab(tab.id)} className={`cursor-pointer rounded-full px-6 py-2.5 font-display text-xs ${libraryTab === tab.id ? "bg-accent text-accent-foreground shadow-xs" : "text-muted-foreground border-border border-2"}`}>
            {tab.label}
          </button>
        ))}
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
            {libraryTab === "saved" ? (
              <ExploreLinkCard />
            ) : (
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
