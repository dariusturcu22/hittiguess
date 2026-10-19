"use client";

import React, { use } from "react";
import { AddSongForm } from "./AddSongForm";

interface PageProps {
  params: Promise<{ playlistId: string }>;
}

export default function AddSongPage({ params }: PageProps) {
  const { playlistId: rawId } = use(params);
  const playlistId = parseInt(rawId);
  const backPath = `/playlists/${playlistId}`;

  return <AddSongForm playlistId={playlistId} backPath={backPath} />;
}
