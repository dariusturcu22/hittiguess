import { Button } from "@/components/shadcn/button";
import { IconExternalLink } from "@tabler/icons-react";
import Link from "next/link";
import { SongDTO } from "@/hooks/models";

interface SongReadOnlyViewProps {
  song: SongDTO;
  backPath: string;
}

// VERIFIED is a pipeline-established lock and NEEDS_REVIEW is an LLM-reconciled
// year; hand-editing either would undermine the trust tier the pipeline already
// assigned it, enforced server-side regardless of what this view shows.
export function SongReadOnlyView({ song, backPath }: SongReadOnlyViewProps) {
  const artistNames = song.artists.map((artist) => artist.name).join(", ");

  return (
    <div className="mx-auto w-full max-w-xl flex flex-col gap-6">
      <div className="grid gap-4">
        <div className="flex justify-center">
          <div className="aspect-video w-full max-w-xs overflow-hidden rounded-lg border bg-muted shadow-sm">
            <iframe
              width="100%"
              height="100%"
              src={`https://www.youtube.com/embed/${song.youtubeId}`}
              title="YouTube video player"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
            ></iframe>
          </div>
        </div>
        <div className="flex justify-center">
          <Button variant="outline" size="icon" asChild>
            <a
              href={`https://www.youtube.com/watch?v=${song.youtubeId}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              <IconExternalLink className="size-4" />
            </a>
          </Button>
        </div>
      </div>

      <div className="flex flex-col items-center py-4">
        <div
          className="relative aspect-square w-50 rounded-lg shadow-xl flex flex-col items-center justify-between p-4 text-white overflow-hidden"
          style={{
            background: `linear-gradient(to bottom, ${song.gradientColor1 ? `#${song.gradientColor1}` : "#8B5CF6"}, ${song.gradientColor2 ? `#${song.gradientColor2}` : "#EC4899"})`,
            fontFamily: "'Kanit', sans-serif",
          }}
        >
          <div
            className="mt-2 text-center font-normal leading-tight"
            style={{ fontSize: "15px" }}
          >
            {artistNames}
          </div>
          <div
            className="font-medium tracking-tighter"
            style={{ fontSize: "62px" }}
          >
            {song.releaseYear}
          </div>
          <div
            className="mb-2 text-center italic font-light leading-tight"
            style={{ fontSize: "15px" }}
          >
            {song.title}
          </div>
        </div>
      </div>

      <p className="text-sm text-muted-foreground text-center">
        This song&apos;s release year has been verified and can no longer be
        edited directly.
      </p>

      <div className="flex gap-3 justify-center">
        <Button variant="outline" className="px-10" asChild>
          <Link href={backPath}>Back</Link>
        </Button>
      </div>
    </div>
  );
}
