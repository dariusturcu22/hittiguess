"use client";

import React, { use } from "react";

import { toast } from "sonner";

import PlaylistContent from "./PlaylistContent";
import {
  getGetPlaylistQueryKey,
  useGetPlaylist,
  useUpdatePlaylist,
} from "@/hooks/generated/playlist-management/playlist-management";
import { getGetUserPlaylistsQueryKey } from "@/hooks/generated/user-management/user-management";
import { useQueryClient } from "@tanstack/react-query";

interface PageProps {
  params: Promise<{ playlistId: string }>;
}

export default function PlaylistPage({ params }: PageProps) {
  const { playlistId: rawId } = use(params);
  const playlistId = parseInt(rawId);
  const { data: currentPlaylist, isLoading, isError } =
    useGetPlaylist(playlistId);
  const { mutate: updatePlaylist } = useUpdatePlaylist();
  const queryClient = useQueryClient();

  const handleTitleChange = (name: string) => {
    updatePlaylist(
      { playlistId, data: { name } },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({
            queryKey: getGetUserPlaylistsQueryKey(),
          });
          queryClient.invalidateQueries({
            queryKey: getGetPlaylistQueryKey(playlistId),
          });
        },
        onError: () => {
          toast.error("Couldn't rename the playlist. Try again.");
        },
      },
    );
  };

  const handleColorChange = (color: string) => {
    updatePlaylist(
      { playlistId, data: { color: color.replace("#", "") } },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({
            queryKey: getGetPlaylistQueryKey(playlistId),
          });
        },
        onError: () => {
          toast.error("Couldn't update the playlist color. Try again.");
        },
      },
    );
  };

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

  return (
    <PlaylistContent
      playlistId={playlistId}
      onTitleChange={handleTitleChange}
      onColorChange={handleColorChange}
    />
  );
}
