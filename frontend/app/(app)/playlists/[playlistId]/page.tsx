"use client";

import { AppSidebar } from "@/components/app-sidebar";
import { SiteHeader } from "@/components/site-header";
import { SidebarInset, SidebarProvider } from "@/components/shadcn/sidebar";
import React, { use } from "react";

import { toast } from "sonner";

import PlaylistContent from "./PlaylistContent";
import {
  getGetUserPlaylistsQueryKey,
  useGetUserPlaylists,
} from "@/hooks/generated/user-management/user-management";
import {
  getGetPlaylistQueryKey,
  useUpdatePlaylist,
} from "@/hooks/generated/playlist-management/playlist-management";
import { useQueryClient } from "@tanstack/react-query";

interface PageProps {
  params: Promise<{ playlistId: string }>;
}

export default function PlaylistPage({ params }: PageProps) {
  const { playlistId: rawId } = use(params);
  const playlistId = parseInt(rawId);
  const { data: playlists, isLoading, isError } = useGetUserPlaylists();
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

  if (isError) {
    return <div>Failed to load playlists</div>;
  }

  if (!playlists) {
    return <div>You are not a part of any playlist</div>;
  }

  const currentPlaylist = playlists.find(
    (playlist) => playlist.id === playlistId,
  );

  return (
    <SidebarProvider
      style={
        {
          "--sidebar-width": "calc(var(--spacing) * 72)",
          "--header-height": "calc(var(--spacing) * 12)",
        } as React.CSSProperties
      }
    >
      <AppSidebar
        variant="inset"
        playlists={playlists}
        currentPlaylistId={currentPlaylist?.id}
      />
      <SidebarInset>
        <SiteHeader
          title={currentPlaylist?.name}
          color={
            currentPlaylist?.color ? `#${currentPlaylist.color}` : undefined
          }
          onTitleChange={handleTitleChange}
          onColorChange={handleColorChange}
        />
        <PlaylistContent playlistId={playlistId} />
      </SidebarInset>
    </SidebarProvider>
  );
}
