"use client";

import {
  getGetUserPlaylistsQueryKey,
  useJoinPlaylist,
} from "@/hooks/generated/user-management/user-management";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import React, { use } from "react";
import { toast } from "sonner";
import { IconLoader2 } from "@tabler/icons-react";

interface PageProps {
  params: Promise<{ inviteCode: string }>;
}

export default function JoinPlaylistPage({ params }: PageProps) {
  const { inviteCode } = use(params);
  const router = useRouter();
  const queryClient = useQueryClient();
  const { mutateAsync: joinPlaylist } = useJoinPlaylist();
  const hasAttempted = React.useRef(false);

  React.useEffect(() => {
    if (hasAttempted.current) return;
    hasAttempted.current = true;

    joinPlaylist({ playlistInviteCode: inviteCode, data: {} })
      .then((playlist) => {
        queryClient.invalidateQueries({
          queryKey: getGetUserPlaylistsQueryKey(),
        });
        router.push(`/playlists/${playlist.id}`);
      })
      .catch(() => {
        toast.error("That invite link isn't valid.");
        router.push("/playlists");
      });
  }, [inviteCode, joinPlaylist, queryClient, router]);

  return (
    <div className="flex flex-col items-center justify-center min-h-screen gap-4 bg-dotted">
      <IconLoader2 className="size-8 text-accent animate-spin" />
      <p className="text-muted-foreground text-sm">Joining playlist...</p>
    </div>
  );
}
