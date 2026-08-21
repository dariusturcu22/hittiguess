"use client";

import {
  getGetUserPlaylistsQueryKey,
  useJoinPlaylist,
} from "@/hooks/generated/user-management/user-management";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import React, { use } from "react";
import { toast } from "sonner";

interface PageProps {
  params: Promise<{ inviteCode: string }>;
}

export default function JoinPlaylistPage({ params }: PageProps) {
  const { inviteCode } = use(params);
  const router = useRouter();
  const queryClient = useQueryClient();
  const { mutate: joinPlaylist } = useJoinPlaylist();
  const hasAttempted = React.useRef(false);

  React.useEffect(() => {
    if (hasAttempted.current) return;
    hasAttempted.current = true;

    joinPlaylist(
      {
        playlistInviteCode: inviteCode,
      },
      {
        onSuccess: (playlist) => {
          queryClient.invalidateQueries({
            queryKey: getGetUserPlaylistsQueryKey(),
          });
          router.push(`/playlists/${playlist.id}`);
        },
        onError: () => {
          toast.error("That invite link isn't valid.");
          router.push("/playlists");
        },
      },
    );
  }, [inviteCode, joinPlaylist, queryClient, router]);

  return (
    <div className="flex items-center justify-center min-h-screen">
      <p className="text-muted-foreground">Joining playlist...</p>
    </div>
  );
}
