"use client";

import { AppSidebar } from "@/components/app-sidebar";
import { SiteHeader } from "@/components/site-header";
import { SidebarInset, SidebarProvider } from "@/components/shadcn/sidebar";
import React from "react";

import { useGetUserPlaylists } from "@/hooks/generated/user-management/user-management";

export default function PlaylistPage() {
  const { data: playlists, isLoading, isError } = useGetUserPlaylists();

  if (isLoading) {
    return <div>Loading playlists...</div>;
  }

  if (isError) {
    return <div>Failed to load playlists</div>;
  }

  if (!playlists) {
    return <div>You are not a part of any playlist</div>;
  }

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
        currentPlaylistId={undefined}
      />
      <SidebarInset>
        <SiteHeader title="-- No playlist selected --" />
      </SidebarInset>
    </SidebarProvider>
  );
}
