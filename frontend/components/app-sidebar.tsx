"use client";

import * as React from "react";
import axios from "axios";
import { Music, Plus, LogIn } from "lucide-react";
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
import { LogoBars } from "@/components/logo";

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

const APPLICATION_NAME = "hittiguess";

/* The nav rail in the mockups draws its own separators and active state
   rather than leaning on the shadcn defaults: 14px radius, an accent fill
   with a hard offset shadow when active, a 2px outline when not. */
const NAV_ITEM_CLASSES =
  "rounded-[14px] h-10 border-2 border-transparent font-sans font-semibold " +
  "data-[active=true]:bg-sidebar-primary data-[active=true]:text-sidebar-primary-foreground " +
  "data-[active=true]:border-sidebar-primary data-[active=true]:shadow-xs " +
  "data-[active=true]:font-display data-[active=true]:text-xs data-[active=true]:tracking-wide " +
  "hover:border-sidebar-border";

export function AppSidebar({
  playlists = [],
  currentPlaylistId,
  ...props
}: AppSidebarProps) {
  playlists = Array.isArray(playlists) ? playlists : [];

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
      { playlistInviteCode: joinId, data: {} },
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
      <SidebarHeader className="border-b-[3px] border-sidebar-border pb-3">
        <div className="flex items-center gap-2 px-2 py-1.5">
          <LogoBars />
          {state !== "collapsed" && (
            <span className="font-wordmark font-extrabold text-lg text-sidebar-foreground">
              {APPLICATION_NAME}
            </span>
          )}
        </div>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel className="font-display text-[10px] tracking-widest text-muted-foreground">
            Playlists
          </SidebarGroupLabel>
          <SidebarMenu>
            {playlists.map((playlist) => (
              <SidebarMenuItem key={playlist.id}>
                <SidebarMenuButton
                  asChild
                  isActive={currentPlaylistId === playlist.id}
                  tooltip={playlist.name}
                  className={NAV_ITEM_CLASSES}
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
                className={`cursor-pointer text-muted-foreground hover:text-sidebar-foreground ${NAV_ITEM_CLASSES}`}
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
                className={`cursor-pointer text-muted-foreground hover:text-sidebar-foreground ${NAV_ITEM_CLASSES}`}
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
                      className="h-8 rounded-full text-xs border-sidebar-border"
                    />
                    <Button
                      size="xs"
                      className="h-8 px-3 shrink-0"
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
      <SidebarFooter className="border-t-[3px] border-sidebar-border pt-3">
        <NavUser />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
