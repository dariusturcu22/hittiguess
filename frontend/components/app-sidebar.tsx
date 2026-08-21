"use client";

import * as React from "react";
import axios from "axios";
import { AudioWaveform, Music, Plus, LogIn } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { NavUser } from "@/components/nav-user";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarRail,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  useSidebar,
} from "@/components/shadcn/sidebar";
import { Input } from "@/components/shadcn/input";
import { Button } from "@/components/shadcn/button";

import { PlaylistSummaryDTO } from "@/hooks/models";

import {
  useCreatePlaylist,
  useJoinPlaylist,
  getGetUserPlaylistsQueryKey,
} from "@/hooks/generated/user-management/user-management";
import { useQueryClient } from "@tanstack/react-query";

interface AppSidebarProps extends React.ComponentProps<typeof Sidebar> {
  playlists?: PlaylistSummaryDTO[];
  currentPlaylistId?: number;
}

const application = {
  name: "My Hitster",
  logo: AudioWaveform,
};

export function AppSidebar({
  playlists = [],
  currentPlaylistId,
  ...props
}: AppSidebarProps) {
  playlists = Array.isArray(playlists) ? playlists : [];

  const Logo = application.logo;
  const { state } = useSidebar();
  const router = useRouter();
  const queryClient = useQueryClient();

  const { mutate: createPlaylist, isPending: isCreating } = useCreatePlaylist();
  const { mutate: joinPlaylist, isPending: isJoining } = useJoinPlaylist();

  const [joinExpanded, setJoinExpanded] = React.useState(false);
  const [joinId, setJoinId] = React.useState("");
  const [joinError, setJoinError] = React.useState("");
  const joinInputRef = React.useRef<HTMLInputElement>(null);

  const handleAddPlaylist = () => {
    createPlaylist(undefined, {
      onSuccess: (newPlaylist) => {
        queryClient.invalidateQueries({
          queryKey: getGetUserPlaylistsQueryKey(),
        });
        router.push(`/playlists/${newPlaylist.id}`);
      },
      onError: () => {
        setJoinError("Failed to create playlist");
      },
    });
  };

  const handleJoinPlaylist = () => {
    if (!joinId.trim()) {
      setJoinError("Enter a valid invite code");
      return;
    }

    joinPlaylist(
      { playlistInviteCode: joinId },
      {
        onSuccess: (playlist) => {
          queryClient.invalidateQueries({
            queryKey: getGetUserPlaylistsQueryKey(),
          });
          setJoinExpanded(false);
          router.push(`/playlists/${playlist.id}`);
        },
        onError: (error: unknown) => {
          const message = axios.isAxiosError<{ message?: string }>(error)
            ? error.response?.data?.message
            : undefined;
          setJoinError(message || "Playlist not found");
        },
      },
    );
  };

  return (
    <Sidebar collapsible="icon" {...props}>
      <SidebarHeader>
        <div className="flex items-center gap-2 px-2 py-1.5">
          <Logo className="h-5 w-5" />
          {state !== "collapsed" && (
            <span className="font-semibold">{application.name}</span>
          )}
        </div>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Playlists</SidebarGroupLabel>
          <SidebarMenu>
            {playlists.map((playlist) => (
              <SidebarMenuItem key={playlist.id}>
                <SidebarMenuButton
                  asChild
                  isActive={currentPlaylistId === playlist.id}
                  tooltip={playlist.name}
                >
                  <Link href={`/playlists/${playlist.id}`}>
                    <Music className="size-4" />
                    <span>{playlist.name}</span>
                    {state !== "collapsed" && (
                      <span className="ml-auto text-xs text-muted-foreground">
                        {playlist.songCount}
                      </span>
                    )}
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            ))}

            <SidebarMenuItem>
              <SidebarMenuButton
                tooltip="Add Playlist"
                onClick={handleAddPlaylist}
                disabled={isCreating}
                className="cursor-pointer text-muted-foreground hover:text-foreground"
              >
                <Plus className="size-4" />
                {state !== "collapsed" && (
                  <span>{isCreating ? "Creating..." : "Add Playlist"}</span>
                )}
              </SidebarMenuButton>
            </SidebarMenuItem>

            <SidebarMenuItem>
              <SidebarMenuButton
                tooltip="Join Playlist"
                onClick={() => setJoinExpanded((prev) => !prev)}
                className="cursor-pointer text-muted-foreground hover:text-foreground"
              >
                <LogIn className="size-4" />
                {state !== "collapsed" && <span>Join Playlist</span>}
              </SidebarMenuButton>

              {joinExpanded && state !== "collapsed" && (
                <div className="mt-1 px-2 flex flex-col gap-1.5 animate-in fade-in slide-in-from-top-1 duration-150">
                  <div className="flex gap-1.5">
                    <Input
                      ref={joinInputRef}
                      placeholder="Invite code"
                      value={joinId}
                      onChange={(e) => {
                        setJoinId(e.target.value);
                        setJoinError("");
                      }}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") handleJoinPlaylist();
                        if (e.key === "Escape") setJoinExpanded(false);
                      }}
                      className="h-7 text-xs"
                    />
                    <Button
                      size="sm"
                      className="h-7 px-2 text-xs shrink-0"
                      onClick={handleJoinPlaylist}
                      disabled={isJoining}
                    >
                      {isJoining ? "..." : "Join"}
                    </Button>
                  </div>
                  {joinError && (
                    <p className="text-xs text-destructive px-1">{joinError}</p>
                  )}
                </div>
              )}
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroup>
      </SidebarContent>
      <SidebarFooter>
        <NavUser />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
