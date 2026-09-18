"use client";

import React, { use } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AlertCircle, ListMusic, LoaderCircle, Search } from "lucide-react";

import { useGetUserPlaylists } from "@/hooks/generated/user-management/user-management";
import { useGetPublicPlaylists } from "@/hooks/generated/playlist-management/playlist-management";

interface PageProps {
  params: Promise<{ playlistId: string }>;
}

interface SourceCandidate {
  id: number;
  name: string;
  songCount: number;
  color?: string;
  provenance: string;
}

export default function ImportFromPlaylistSelectPage({ params }: PageProps) {
  const { playlistId: rawId } = use(params);
  const destinationPlaylistId = parseInt(rawId);
  const router = useRouter();

  const { data: ownPlaylists, isLoading: isLoadingOwn, isError: hasOwnError, refetch: refetchOwn } = useGetUserPlaylists();
  const { data: publicPlaylists, isLoading: isLoadingPublic, isError: hasPublicError, refetch: refetchPublic } = useGetPublicPlaylists();

  const [searchTerm, setSearchTerm] = React.useState("");
  const [selectedId, setSelectedId] = React.useState<number | null>(null);

  // Both PlaylistSummaryDTO.color and PublicPlaylistSummaryDTO.color come
  // back as raw 6-hex-digit strings with no leading "#", so both paths need
  // the same prefix before they can be used as a CSS color.
  const candidates: SourceCandidate[] = [
    ...(ownPlaylists ?? [])
      .filter((playlist) => playlist.id !== destinationPlaylistId)
      .map((playlist) => ({
        id: playlist.id,
        name: playlist.name,
        songCount: playlist.songCount,
        color: playlist.color ? `#${playlist.color}` : undefined,
        provenance: "owned by you",
      })),
    ...(publicPlaylists ?? []).map((playlist) => ({
      id: playlist.id,
      name: playlist.name,
      songCount: playlist.songCount,
      color: playlist.color ? `#${playlist.color}` : undefined,
      provenance: `public, by ${playlist.owner?.username ?? "unknown"}`,
    })),
  ];

  const normalizedSearch = searchTerm.trim().toLowerCase();
  const isLoading = isLoadingOwn || isLoadingPublic;
  const hasError = hasOwnError || hasPublicError;
  const visibleCandidates = candidates.filter((candidate) => {
    if (normalizedSearch.length === 0) {
      return true;
    }
    return `${candidate.name} ${candidate.provenance}`
      .toLowerCase()
      .includes(normalizedSearch);
  });

  function handleContinue() {
    if (selectedId == null) {
      return;
    }
    router.push(
      `/playlists/${destinationPlaylistId}/import/from-playlist/${selectedId}`,
    );
  }

  return (
    <div className="flex-1 min-h-0 flex justify-center px-14 pt-11 pb-10">
      <div className="w-[640px] max-w-full flex flex-col">
        <h1 className="font-display text-[28px] text-destructive mb-1.5 [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
          Import from a playlist
        </h1>
        <p className="text-[13px] text-muted-foreground mb-[18px]">
          Pick one you&apos;ve joined or found public. Every song copies over
          instantly.
        </p>

        <div className="relative mb-4 shrink-0">
          <Search className="size-4 text-muted-foreground absolute left-[18px] top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            placeholder="Search your playlists and public ones..."
            className="w-full box-border bg-background border-2 border-secondary rounded-full py-[15px] pl-[46px] pr-5 font-sans text-[15px] text-card-foreground"
          />
        </div>

        <div className="bg-card border-[3px] border-border-strong rounded-2xl shadow-lg box-border overflow-hidden flex-1 min-h-0 flex flex-col">
          <div className="flex-1 min-h-0 overflow-y-auto">
            {isLoading ? (
              <div className="flex min-h-[260px] flex-col items-center justify-center gap-3 p-6 text-center text-[13px] text-muted-foreground">
                <LoaderCircle className="size-6 animate-spin text-primary" />
                Finding playlists you can copy from...
              </div>
            ) : hasError ? (
              <div className="flex min-h-[260px] flex-col items-center justify-center gap-3 p-6 text-center">
                <AlertCircle className="size-6 text-destructive" />
                <p className="text-[13px] text-muted-foreground">Couldn&apos;t load playlists right now.</p>
                <button type="button" onClick={() => { void refetchOwn(); void refetchPublic(); }} className="font-display text-[11px] text-primary">Try again</button>
              </div>
            ) : visibleCandidates.length === 0 ? (
              <div className="flex min-h-[260px] flex-col items-center justify-center gap-2 p-6 text-center text-[13px] text-muted-foreground">
                <ListMusic className="size-7 text-icon-muted" />
                <p>{normalizedSearch ? "No playlists match that search." : "No playlists to import from yet."}</p>
              </div>
            ) : (
              visibleCandidates.map((candidate) => {
                const selected = selectedId === candidate.id;
                return (
                  <button
                    type="button"
                    key={candidate.id}
                    onClick={() => setSelectedId(candidate.id)}
                    className={`flex w-full items-center gap-[14px] px-5 py-[14px] border-b-2 border-background text-left last:border-b-0 cursor-pointer ${
                      selected ? "bg-destructive/10" : ""
                    }`}
                  >
                    <span
                      className={`w-5 h-5 rounded-full border-2 shrink-0 flex items-center justify-center ${
                        selected ? "border-destructive" : "border-secondary"
                      }`}
                    >
                      {selected ? (
                        <span className="w-2.5 h-2.5 rounded-full bg-destructive" />
                      ) : null}
                    </span>
                    <span
                      className="w-11 h-11 rounded-[10px] shrink-0 flex items-center justify-center"
                      style={{ background: candidate.color }}
                    >
                      <ListMusic className="size-[18px] text-foreground/40" />
                    </span>
                    <span className="flex-1 min-w-0">
                      <span className="block text-sm text-card-foreground truncate">
                        {candidate.name}
                      </span>
                      <span className="mt-0.5 block text-[12px] text-muted-foreground">
                        {candidate.songCount} songs &middot; {candidate.provenance}
                      </span>
                    </span>
                  </button>
                );
              })
            )}
          </div>
        </div>

        <div className="flex items-center gap-4 mt-5 shrink-0">
          <button
            type="button"
            onClick={handleContinue}
            disabled={selectedId == null}
            className="flex-1 font-display text-sm text-primary-foreground bg-primary py-[15px] rounded-full shadow-sm box-border cursor-pointer text-center disabled:opacity-60 disabled:cursor-not-allowed"
          >
            Continue
          </button>
          <Link
            href={`/playlists/${destinationPlaylistId}/import`}
            className="text-[12px] text-muted-foreground cursor-pointer whitespace-nowrap hover:text-card-foreground"
          >
            ‹ Back
          </Link>
        </div>
      </div>
    </div>
  );
}
