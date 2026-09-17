"use client";

import Link from "next/link";
import { use } from "react";
import { Video, ListMusic, ChevronRight } from "lucide-react";

interface PageProps {
  params: Promise<{ playlistId: string }>;
}

function SourceOption({
  href,
  icon,
  title,
  description,
}: {
  href: string;
  icon: React.ReactNode;
  title: string;
  description: string;
}) {
  return (
    <Link
      href={href}
      className="w-full bg-background border-[3px] border-secondary rounded-2xl box-border p-7 flex items-center gap-5 cursor-pointer hover:border-border-strong transition-colors"
    >
      <div className="w-14 h-14 rounded-2xl flex items-center justify-center shrink-0 bg-destructive/15 text-destructive">
        {icon}
      </div>
      <div className="flex-1">
        <div className="font-display text-sm text-card-foreground mb-1">
          {title}
        </div>
        <div className="text-[12px] text-muted-foreground leading-[1.5]">
          {description}
        </div>
      </div>
      <ChevronRight className="size-[18px] text-muted-foreground shrink-0" />
    </Link>
  );
}

export default function ImportChooseSourcePage({ params }: PageProps) {
  const { playlistId: rawId } = use(params);
  const playlistId = parseInt(rawId);
  const backPath = `/playlists/${playlistId}`;

  return (
    <div className="flex-1 flex items-center justify-center px-6 py-12">
      <div className="w-[560px] max-w-full bg-card border-[3px] border-border-strong rounded-2xl shadow-lg box-border px-12 pt-11 pb-12 flex flex-col items-center">
        <h1 className="w-full font-display text-2xl text-destructive text-center mb-2 [text-shadow:3px_3px_0_var(--text-shadow-on-card)]">
          Import playlist
        </h1>
        <p className="text-[13px] text-muted-foreground text-center mb-7 leading-[1.5]">
          Where are the songs coming from?
        </p>

        <div className="w-full flex flex-col gap-4">
          <SourceOption
            href={`/playlists/${playlistId}/import/youtube`}
            icon={<Video className="size-6" />}
            title="From YouTube"
            description="Paste a YouTube playlist link, we'll crawl and fetch every song."
          />
          <SourceOption
            href={`/playlists/${playlistId}/import/from-playlist`}
            icon={<ListMusic className="size-6" />}
            title="From an existing playlist"
            description="Copy every song from a playlist you've joined or found public. Instant, no fetching needed."
          />
        </div>

        <Link
          href={backPath}
          className="text-[12px] text-muted-foreground mt-6 cursor-pointer hover:text-card-foreground"
        >
          ‹ Back
        </Link>
      </div>
    </div>
  );
}
