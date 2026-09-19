import { Play } from "lucide-react";

import { cn } from "@/lib/utils";

const MOSAIC_TILE_COUNT = 4;

interface PlaylistCoverMosaicProps {
  /** Up to 4 YouTube ids, first-in-playlist first. Fewer than 4 (including
   * none) falls back to a placeholder tile for the remaining slots, rather
   * than inventing a color for songs that aren't there. */
  previewYoutubeIds: string[];
  className?: string;
}

function thumbnailUrl(youtubeId: string): string {
  return `https://i.ytimg.com/vi/${youtubeId}/hqdefault.jpg`;
}

export function PlaylistCoverMosaic({ previewYoutubeIds, className }: PlaylistCoverMosaicProps) {
  const tiles = Array.from({ length: MOSAIC_TILE_COUNT }, (_, index) => previewYoutubeIds[index] ?? null);

  return (
    <div
      className={cn(
        "grid aspect-square w-full shrink-0 grid-cols-2 grid-rows-2 gap-0.5 overflow-hidden rounded-2xl border-[3px] border-border-strong bg-border-strong shadow-lg",
        className,
      )}
    >
      {tiles.map((youtubeId, index) =>
        youtubeId ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            key={index}
            src={thumbnailUrl(youtubeId)}
            alt=""
            className="size-full object-cover"
          />
        ) : (
          <div key={index} className="flex items-center justify-center bg-secondary">
            <Play className="size-5 fill-muted-foreground/55 text-muted-foreground/55" />
          </div>
        ),
      )}
    </div>
  );
}
