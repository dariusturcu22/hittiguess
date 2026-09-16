"use client";

import { useState } from "react";
import { Loader2, Search } from "lucide-react";

import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";

const YOUTUBE_ID_PATTERN = /^[a-zA-Z0-9_-]{11}$/;

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
}

export function NewSongLinkStep({
  onFetch,
  isFetching,
  onBackToSearch,
}: NewSongLinkStepProps) {
  const [input, setInput] = useState("");
  const [error, setError] = useState("");

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
        <label className="text-[13px] font-semibold text-muted-foreground">
          YouTube link
        </label>
        <Input
          autoFocus
          value={input}
          onChange={(event) => {
            setInput(event.target.value);
            setError("");
          }}
          onKeyDown={(event) => event.key === "Enter" && handleFetch()}
          placeholder="https://youtube.com/watch?v=..."
        />
        {error && <p className="text-sm text-destructive">{error}</p>}
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
