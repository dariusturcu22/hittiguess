import { Play } from "lucide-react";
import React from "react";

import { cn } from "@/lib/utils";

const MOSAIC_TILE_COUNT = 4;

interface PlaylistCoverMosaicProps {
  /** Up to 4 YouTube ids, first-in-playlist first. Fewer than 4 (including
   * none) falls back to a placeholder tile for the remaining slots, rather
   * than inventing a color for songs that aren't there. */
  previewYoutubeIds: string[];
  /** A custom uploaded cover, shown instead of the mosaic while it loads. */
  customCoverUrl?: string;
  className?: string;
}

function thumbnailUrl(youtubeId: string): string {
  return `https://i.ytimg.com/vi/${youtubeId}/hqdefault.jpg`;
}

export function PlaylistCoverMosaic({ previewYoutubeIds, customCoverUrl, className }: PlaylistCoverMosaicProps) {
  const tiles = Array.from({ length: MOSAIC_TILE_COUNT }, (_, index) => previewYoutubeIds[index] ?? null);
  const [customCoverFailed, setCustomCoverFailed] = React.useState(false);
  const showCustomCover = Boolean(customCoverUrl) && !customCoverFailed;
  React.useEffect(() => {
    setCustomCoverFailed(false);
  }, [customCoverUrl]);

  return (
    <div
      className={cn(
        "grid aspect-square w-full shrink-0 grid-cols-2 grid-rows-2 gap-0.5 overflow-hidden rounded-2xl border-[3px] border-border-strong bg-border-strong shadow-lg",
        className,
      )}
    >
      {showCustomCover ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={customCoverUrl}
          alt=""
          onError={() => setCustomCoverFailed(true)}
          className="col-span-2 row-span-2 size-full object-cover [image-rendering:pixelated]"
        />
      ) : (
        tiles.map((youtubeId, index) =>
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
        )
      )}
    </div>
  );
}
