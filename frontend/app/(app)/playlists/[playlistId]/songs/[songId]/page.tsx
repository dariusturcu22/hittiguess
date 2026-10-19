"use client";

import React, { use } from "react";
import { SongForm } from "./SongForm";
import { SongReadOnlyView } from "./SongReadOnlyView";
import {
  useGetPlaylist,
  useGetSong,
} from "@/hooks/generated/playlist-management/playlist-management";
import { SongDTOVerificationStatus } from "@/hooks/models";

const EDITABLE_VERIFICATION_STATUSES: string[] = [
  SongDTOVerificationStatus.UNVERIFIED,
  SongDTOVerificationStatus.MANUAL_ENTRY,
];

interface PageProps {
  params: Promise<{ playlistId: string; songId: string }>;
}

export default function SongDetailPage({ params }: PageProps) {
  const { playlistId: rawPlaylistId, songId: rawSongId } = use(params);
  const playlistId = parseInt(rawPlaylistId);
  const songId = parseInt(rawSongId);
  const backPath = `/playlists/${playlistId}`;

  const { data: song, isLoading } = useGetSong(playlistId, songId);
  const { data: playlist } = useGetPlaylist(playlistId);

  if (isLoading || !song) {
    return (
      <div className="flex h-full items-center justify-center p-6">
        <p className="text-sm text-muted-foreground">Loading song...</p>
      </div>
    );
  }

  return (
    <div className="flex h-full items-center justify-center p-6">
      <div className="w-full max-w-[640px] rounded-2xl border-[3px] border-border-strong bg-card p-8 shadow-lg sm:p-11">
        {EDITABLE_VERIFICATION_STATUSES.includes(song.verificationStatus) ? (
          <SongForm
            song={song}
            playlistId={playlistId}
            playlistName={playlist?.name}
            backPath={backPath}
          />
        ) : (
          <SongReadOnlyView
            song={song}
            playlistId={playlistId}
            backPath={backPath}
          />
        )}
      </div>
    </div>
  );
}
