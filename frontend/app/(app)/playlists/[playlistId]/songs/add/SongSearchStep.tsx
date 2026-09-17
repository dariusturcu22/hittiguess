"use client";

import { useState } from "react";
import Link from "next/link";
import { Check, Plus, Search, X } from "lucide-react";

import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";
import { SongDTO } from "@/hooks/models";
import { useSearchSongs } from "@/hooks/generated/song-search/song-search";

const MIN_QUERY_LENGTH = 2;

interface SongSearchStepProps {
  backPath: string;
  queue: SongDTO[];
  onToggleQueued: (song: SongDTO) => void;
  onSubmitQueue: () => void;
  isSubmitting: boolean;
  onStartNewSong: () => void;
}

export function SongSearchStep({
  backPath,
  queue,
  onToggleQueued,
  onSubmitQueue,
  isSubmitting,
  onStartNewSong,
}: SongSearchStepProps) {
  const [query, setQuery] = useState("");
  const isQueryLongEnough = query.trim().length >= MIN_QUERY_LENGTH;

  const { data: results, isLoading } = useSearchSongs(
    { query: query.trim() },
    { query: { enabled: isQueryLongEnough } },
  );

  const queuedIds = new Set(queue.map((song) => song.id));

  return (
    <div className="flex h-full flex-col p-6 md:p-11">
      <Link
        href={backPath}
        className="mb-3 flex-shrink-0 self-start text-xs text-muted-foreground hover:text-foreground"
      >
        ‹ Back to playlist
      </Link>

      <div className="mb-5.5 flex flex-shrink-0 flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1
            className="font-display text-[26px] text-accent sm:text-[30px]"
            style={{ textShadow: "3px 3px 0 var(--text-shadow-on-page)" }}
          >
            Add songs
          </h1>
          <p className="mt-1.5 text-[13px] text-muted-foreground">
            Search the database, or add a track that isn&apos;t in it yet.
          </p>
        </div>
        <Button variant="outline" className="gap-2" onClick={onStartNewSong}>
          <Plus className="size-3.5" />
          Add a new song
        </Button>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-6 lg:flex-row">
        <div className="flex min-h-0 min-w-0 flex-1 flex-col">
          <div className="relative mb-3 flex-shrink-0">
            <Search className="pointer-events-none absolute top-1/2 left-4.5 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search by title or artist..."
              className="h-12 rounded-full pl-11 text-sm"
            />
          </div>

          {isQueryLongEnough && (
            <div className="mb-2.5 flex-shrink-0 text-xs text-muted-foreground">
              {isLoading
                ? "Searching..."
                : `${results?.length ?? 0} result${results?.length === 1 ? "" : "s"}`}
            </div>
          )}

          <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border-[3px] border-border-strong bg-card shadow-lg">
            <div className="min-h-0 flex-1 overflow-y-auto">
              {!isQueryLongEnough && (
                <p className="p-8 text-center text-sm text-muted-foreground">
                  Type at least {MIN_QUERY_LENGTH} characters to search the
                  catalog.
                </p>
              )}
              {isQueryLongEnough && !isLoading && results?.length === 0 && (
                <p className="p-8 text-center text-sm text-muted-foreground">
                  No matches. Try &quot;Add a new song&quot; instead.
                </p>
              )}
              {isQueryLongEnough &&
                results?.map((song) => {
                  const isQueued = queuedIds.has(song.id);
                  return (
                    <div
                      key={song.id}
                      className="flex items-center gap-3.5 border-b-2 border-background px-5 py-3.5 last:border-b-0"
                    >
                      <div className="min-w-0 flex-1">
                        <div className="truncate text-sm">{song.title}</div>
                        <div className="truncate text-xs text-muted-foreground">
                          {song.artists.map((artist) => artist.name).join(", ")}
                          {" • "}
                          {song.releaseYear}
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => onToggleQueued(song)}
                        title={isQueued ? "Remove from queue" : "Add to queue"}
                        className={`flex size-8 shrink-0 items-center justify-center rounded-[10px] ${
                          isQueued
                            ? "bg-primary/18 text-primary"
                            : "bg-primary text-primary-foreground"
                        }`}
                      >
                        {isQueued ? (
                          <Check className="size-3.5" />
                        ) : (
                          <Plus className="size-3.5" />
                        )}
                      </button>
                    </div>
                  );
                })}
            </div>
          </div>
        </div>

        <div className="flex w-full shrink-0 lg:w-[340px]">
          <div className="flex w-full flex-col rounded-2xl border-[3px] border-border-strong bg-card p-5 shadow-lg">
            <div className="mb-1 flex-shrink-0 font-display text-[11px] tracking-wide text-muted-foreground uppercase">
              Songs to add ({queue.length})
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto">
              {queue.length === 0 && (
                <p className="py-6 text-center text-xs text-muted-foreground">
                  Nothing queued yet. Add songs from the search results.
                </p>
              )}
              {queue.map((song) => (
                <div
                  key={song.id}
                  className="flex items-center gap-2.5 border-b-2 border-background py-2.5 last:border-b-0"
                >
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[13px]">{song.title}</div>
                    <div className="truncate text-[11px] text-muted-foreground">
                      {song.artists.map((artist) => artist.name).join(", ")}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => onToggleQueued(song)}
                    title="Remove from queue"
                    className="flex size-6 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:text-destructive"
                  >
                    <X className="size-3.5" />
                  </button>
                </div>
              ))}
            </div>

            <Button
              className="mt-3.5 w-full flex-shrink-0"
              disabled={queue.length === 0 || isSubmitting}
              onClick={onSubmitQueue}
            >
              {isSubmitting
                ? "Adding..."
                : `Add ${queue.length} song${queue.length === 1 ? "" : "s"} to playlist`}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
