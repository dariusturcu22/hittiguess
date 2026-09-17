import { CreateSongRequest, SongDTO } from "@/hooks/models";

const DEFAULT_GRADIENT_1 = "8B5CF6";
const DEFAULT_GRADIENT_2 = "EC4899";
const UNKNOWN_ARTIST_LABEL = "Unknown artist";

export function buildCreateSongRequestFromCatalog(
  song: SongDTO,
): CreateSongRequest {
  return {
    youtubeId: song.youtubeId,
    title: song.title,
    artist: song.artists[0]?.name ?? UNKNOWN_ARTIST_LABEL,
    releaseYear: song.releaseYear,
    gradientColor1: song.gradientColor1 ?? DEFAULT_GRADIENT_1,
    gradientColor2: song.gradientColor2 ?? DEFAULT_GRADIENT_2,
    country: song.country,
  };
}
