import { SongDTOVerificationStatus } from "@/hooks/models";

interface SongAttentionMetadata {
  verificationStatus?: SongDTOVerificationStatus | string;
  confidence?: string;
  needsUserAttention?: boolean;
}

export function needsUserAttention(song: SongAttentionMetadata) {
  if (song.needsUserAttention !== undefined) {
    return song.needsUserAttention;
  }
  return (
    song.verificationStatus === SongDTOVerificationStatus.MANUAL_ENTRY ||
    (song.verificationStatus === SongDTOVerificationStatus.NEEDS_REVIEW &&
      song.confidence?.toLowerCase() === "low")
  );
}
