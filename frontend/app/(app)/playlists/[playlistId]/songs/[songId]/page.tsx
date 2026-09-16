"use client";

import { SiteHeader } from "@/components/site-header";
import { Button } from "@/components/shadcn/button";
import { IconArrowLeft } from "@tabler/icons-react";
import Link from "next/link";
import React, { use } from "react";
import { SongForm } from "./SongForm";
import { SongReadOnlyView } from "./SongReadOnlyView";
import { useGetSong } from "@/hooks/generated/playlist-management/playlist-management";
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

  const { data: song } = useGetSong(playlistId, songId);

  return (
    <div className="flex h-full flex-col">
      <SiteHeader title={song?.title} />

      <div className="flex flex-1 flex-col p-4 md:p-6">
        <div className="mb-6">
          <Button variant="ghost" size="sm" asChild>
            <Link href={backPath}>
              <IconArrowLeft className="size-4 mr-2" />
              Back to Playlist
            </Link>
          </Button>
        </div>

        {song && (
          <>
            {EDITABLE_VERIFICATION_STATUSES.includes(
              song.verificationStatus,
            ) ? (
              <SongForm
                song={song}
                backPath={backPath}
                playlistId={playlistId}
              />
            ) : (
              <SongReadOnlyView song={song} backPath={backPath} />
            )}

            <div className="text-center text-[10px] text-muted-foreground mt-4">
              {song.addedBy
                ? `Added by ${song.addedBy.username}`
                : "Added by a deleted account"}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
