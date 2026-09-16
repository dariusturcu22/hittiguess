"use client";

import { SiteHeader } from "@/components/site-header";
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
    return <div>Loading playlists...</div>;
  }

  if (isError || !currentPlaylist) {
    return <div>Failed to load playlist</div>;
  }

  return (
    <div className="flex h-full flex-col">
      <SiteHeader
        title={currentPlaylist.name}
        color={`#${currentPlaylist.color}`}
        onTitleChange={handleTitleChange}
        onColorChange={handleColorChange}
      />
      <PlaylistContent playlistId={playlistId} />
    </div>
  );
}
