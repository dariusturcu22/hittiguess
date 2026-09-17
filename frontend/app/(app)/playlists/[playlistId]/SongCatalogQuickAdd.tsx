"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Check, Plus, Search } from "lucide-react";

import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";
import { SongDTO } from "@/hooks/models";
import { useSearchSongs } from "@/hooks/generated/song-search/song-search";
import {
  getGetPlaylistQueryKey,
  useCreateSong,
} from "@/hooks/generated/playlist-management/playlist-management";
import { buildCreateSongRequestFromCatalog } from "./songs/add/songCatalogRequest";

const MIN_QUERY_LENGTH = 2;

interface SongCatalogQuickAddProps {
  playlistId: number;
}

export function SongCatalogQuickAdd({ playlistId }: SongCatalogQuickAddProps) {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [addedSongIds, setAddedSongIds] = useState<number[]>([]);
  const { mutate: addSong, isPending: isAddingSong } = useCreateSong();

  const isQueryLongEnough = query.trim().length >= MIN_QUERY_LENGTH;
  const { data: results, isLoading } = useSearchSongs(
    { query: query.trim() },
    { query: { enabled: isQueryLongEnough } },
  );

  const handleAdd = (song: SongDTO) => {
    addSong(
      { playlistId, data: buildCreateSongRequestFromCatalog(song) },
      {
        onSuccess: () => {
          setAddedSongIds((previous) => [...previous, song.id]);
          queryClient.invalidateQueries({
            queryKey: getGetPlaylistQueryKey(playlistId),
          });
        },
        onError: () => {
          toast.error(`Couldn't add "${song.title}". Try again.`);
        },
      },
    );
  };

  return (
    <div className="flex flex-col items-center gap-3 p-8">
      <p className="text-center text-sm text-muted-foreground">
        No songs in this playlist yet. Search the catalog to add one.
      </p>
      <div className="relative w-full max-w-[360px]">
        <Search className="pointer-events-none absolute top-1/2 left-3.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search songs..."
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          className="h-9 w-full rounded-full pl-9"
        />
      </div>
      {isQueryLongEnough && (
        <div className="w-full max-w-[360px]">
          {isLoading && (
            <p className="py-2 text-center text-xs text-muted-foreground">
              Searching...
            </p>
          )}
          {!isLoading && results?.length === 0 && (
            <p className="py-2 text-center text-xs text-muted-foreground">
              No matches. Try a different search.
            </p>
          )}
          {results?.map((song) => {
            const alreadyAdded = addedSongIds.includes(song.id);
            const artistNames = song.artists
              .map((artist) => artist.name)
              .join(", ");
            return (
              <div
                key={song.id}
                className="flex items-center justify-between gap-3 border-b border-border py-2 last:border-b-0"
              >
                <div className="min-w-0">
                  <div className="truncate text-sm">{song.title}</div>
                  <div className="truncate text-xs text-muted-foreground">
                    {artistNames} &middot; {song.releaseYear}
                  </div>
                </div>
                <Button
                  size="sm"
                  variant={alreadyAdded ? "outline" : "default"}
                  disabled={alreadyAdded || isAddingSong}
                  onClick={() => handleAdd(song)}
                  className="shrink-0 gap-1.5"
                >
                  {alreadyAdded ? (
                    <>
                      <Check className="size-3" />
                      Added
                    </>
                  ) : (
                    <>
                      <Plus className="size-3" />
                      Add
                    </>
                  )}
                </Button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
