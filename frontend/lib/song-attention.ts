import { SongDTO, SongDTOVerificationStatus } from "@/hooks/models";

export function needsUserAttention(song: Pick<SongDTO, "verificationStatus" | "confidence">) {
  return (
    song.verificationStatus === SongDTOVerificationStatus.MANUAL_ENTRY ||
    (song.verificationStatus === SongDTOVerificationStatus.NEEDS_REVIEW &&
      song.confidence?.toLowerCase() === "low")
  );
}
