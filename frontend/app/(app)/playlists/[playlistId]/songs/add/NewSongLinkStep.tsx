"use client";

import { useEffect, useState } from "react";
import { Loader2, Search } from "lucide-react";

import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";

const YOUTUBE_ID_PATTERN = /^[a-zA-Z0-9_-]{11}$/;
const FETCH_STAGE_ROTATE_MILLISECONDS = 4000;

// The metadata pipeline stages behind one fetch call, shown in rotation while
// the request runs. The call itself reports no live progress, so these name
// the real stages in order rather than the request's current one.
const FETCH_STAGES = [
  "Reading the video title and channel",
  "Consulting MusicBrainz",
  "Consulting Discogs",
  "Consulting Wikidata",
  "Reconciling the release year",
] as const;

function extractYoutubeId(input: string): string | null {
  const trimmed = input.trim();

  if (YOUTUBE_ID_PATTERN.test(trimmed)) {
    return trimmed;
  }

  try {
    const url = new URL(trimmed);
    let candidate: string | null = null;
    if (url.searchParams.get("v")) candidate = url.searchParams.get("v");
    else if (url.hostname === "youtu.be")
      candidate = url.pathname.slice(1).split("?")[0];
    else if (url.pathname.startsWith("/embed/"))
      candidate = url.pathname.split("/embed/")[1].split("?")[0];
    return candidate && YOUTUBE_ID_PATTERN.test(candidate) ? candidate : null;
  } catch {
    return null;
  }
}

interface NewSongLinkStepProps {
  onFetch: (youtubeId: string) => void;
  isFetching: boolean;
  onBackToSearch: () => void;
  fetchError: string;
}

export function NewSongLinkStep({
  onFetch,
  isFetching,
  onBackToSearch,
  fetchError,
}: NewSongLinkStepProps) {
  const [input, setInput] = useState("");
  const [error, setError] = useState("");
  const [fetchStageIndex, setFetchStageIndex] = useState(0);
  const [wasFetching, setWasFetching] = useState(isFetching);
  if (wasFetching !== isFetching) {
    setWasFetching(isFetching);
    if (isFetching) {
      setFetchStageIndex(0);
    }
  }

  useEffect(() => {
    if (!isFetching) {
      return;
    }
    const stageTimer = window.setInterval(() => {
      setFetchStageIndex((currentIndex) => (currentIndex + 1) % FETCH_STAGES.length);
    }, FETCH_STAGE_ROTATE_MILLISECONDS);
    return () => window.clearInterval(stageTimer);
  }, [isFetching]);

  const handleFetch = () => {
    const id = extractYoutubeId(input);
    if (!id) {
      setError(
        "Couldn't extract a YouTube ID from that link. Try a full URL or just the video ID.",
      );
      return;
    }
    setError("");
    onFetch(id);
  };

  return (
    <div className="flex w-full max-w-[460px] flex-col items-center">
      <div className="mb-4.5 rounded-full border-2 border-border bg-background px-3.5 py-1.5 font-display text-[10px] tracking-wide text-muted-foreground">
        STEP 1 OF 2
      </div>

      <h1
        className="mb-2 w-full text-center font-display text-2xl text-accent"
        style={{ textShadow: "3px 3px 0 var(--text-shadow-on-card)" }}
      >
        Add a new song
      </h1>
      <p className="mb-7 text-center text-[13px] leading-relaxed text-muted-foreground">
        Paste a YouTube link. We&apos;ll pull the title, artist, and release
        year automatically.
      </p>

      <div className="mb-7 grid w-full gap-1.5">
        <label
          htmlFor="youtube-link"
          className="text-[13px] font-semibold text-muted-foreground"
        >
          YouTube link
        </label>
        <Input
          id="youtube-link"
          autoFocus
          value={input}
          onChange={(event) => {
            setInput(event.target.value);
            setError("");
          }}
          onKeyDown={(event) => event.key === "Enter" && handleFetch()}
          placeholder="https://youtube.com/watch?v=..."
        />
        {(error || fetchError) && (
          <p className="text-sm text-destructive">{error || fetchError}</p>
        )}
      </div>

      <Button
        className="mb-4.5 w-full gap-2"
        onClick={handleFetch}
        disabled={isFetching}
      >
        {isFetching ? (
          <Loader2 className="size-4 animate-spin" />
        ) : (
          <Search className="size-3.5" />
        )}
        {isFetching ? "Fetching..." : "Fetch details"}
      </Button>

      {isFetching ? (
        <div className="mb-4.5 w-full rounded-2xl border-2 border-border bg-card p-5" aria-live="polite">
          <div className="flex items-center gap-3 text-[13px] font-semibold text-card-foreground">
            <Loader2 className="size-4 animate-spin text-primary" />
            {FETCH_STAGES[fetchStageIndex]}...
          </div>
          <div className="mt-3 flex gap-1.5" aria-hidden="true">
            {FETCH_STAGES.map((stage, stageIndex) => (
              <span
                key={stage}
                className={`h-2 flex-1 rounded-full ${stageIndex <= fetchStageIndex ? "bg-primary" : "bg-secondary"}`}
              />
            ))}
          </div>
        </div>
      ) : null}

      <button
        type="button"
        onClick={onBackToSearch}
        className="text-xs text-muted-foreground hover:text-foreground"
      >
        ‹ Back to search
      </button>
    </div>
  );
}
