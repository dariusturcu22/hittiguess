"use client";
import { SiteHeader } from "@/components/site-header";
import { Button } from "@/components/shadcn/button";
import { IconArrowLeft } from "@tabler/icons-react";
import Link from "next/link";
import React, { use } from "react";
import { AddSongForm } from "./AddSongForm";

interface PageProps {
  params: Promise<{ playlistId: string }>;
}

export default function AddSongPage({ params }: PageProps) {
  const { playlistId: rawId } = use(params);
  const playlistId = parseInt(rawId);
  const backPath = `/playlists/${playlistId}`;

  return (
    <div className="flex h-full flex-col">
      <SiteHeader title="Add Song" />
      <div className="flex flex-1 flex-col p-4 md:p-6">
        <div className="mb-6">
          <Button variant="ghost" size="sm" asChild>
            <Link href={backPath}>
              <IconArrowLeft className="size-4 mr-2" />
              Back to Playlist
            </Link>
          </Button>
        </div>
        <AddSongForm backPath={backPath} playlistId={playlistId} />
      </div>
    </div>
  );
}
