import { SongDTOVerificationStatus } from "@/hooks/models";

interface SongAttentionMetadata {
  verificationStatus?: SongDTOVerificationStatus | string;
  confidence?: string;
}

export function needsUserAttention(song: SongAttentionMetadata) {
  return (
    song.verificationStatus === SongDTOVerificationStatus.MANUAL_ENTRY ||
    (song.verificationStatus === SongDTOVerificationStatus.NEEDS_REVIEW &&
      song.confidence?.toLowerCase() === "low")
  );
}
