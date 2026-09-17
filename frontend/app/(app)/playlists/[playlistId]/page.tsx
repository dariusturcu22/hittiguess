"use client";

import React, { use } from "react";

import PlaylistContent from "./PlaylistContent";
import { useGetPlaylist } from "@/hooks/generated/playlist-management/playlist-management";

interface PageProps {
  params: Promise<{ playlistId: string }>;
}

export default function PlaylistPage({ params }: PageProps) {
  const { playlistId: rawId } = use(params);
  const playlistId = parseInt(rawId);
  const { data: currentPlaylist, isLoading, isError } =
    useGetPlaylist(playlistId);

  if (isLoading) {
    return (
      <div className="flex h-full flex-col p-6 md:p-11">
        <p className="text-sm text-muted-foreground">Loading playlist...</p>
      </div>
    );
  }

  if (isError || !currentPlaylist) {
    return (
      <div className="flex h-full flex-col p-6 md:p-11">
        <p className="text-sm text-destructive">Failed to load playlist.</p>
      </div>
    );
  }

  return <PlaylistContent playlistId={playlistId} />;
}
