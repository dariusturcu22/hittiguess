import { CreateSongRequest, SongDTO } from "@/hooks/models";

const DEFAULT_COLOR = "8B5CF6";
const UNKNOWN_ARTIST_LABEL = "Unknown artist";

export function buildCreateSongRequestFromCatalog(
  song: SongDTO,
): CreateSongRequest {
  return {
    youtubeId: song.youtubeId,
    title: song.title,
    artist: song.artists[0]?.name ?? UNKNOWN_ARTIST_LABEL,
    releaseYear: song.releaseYear,
    color: song.color ?? DEFAULT_COLOR,
    country: song.country,
  };
}
