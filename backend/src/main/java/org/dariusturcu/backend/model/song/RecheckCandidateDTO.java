package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * One song offered for a recheck. lastCheckedAt is when the song's latest pipeline run finished,
 * or when the song was added when no run has finished; it is null only when neither is known.
 * inActiveGame marks songs the recheck skips.
 */
public record RecheckCandidateDTO(
        @NotNull Long songId,
        String title,
        @NotNull List<String> artists,
        Integer releaseYear,
        @NotNull VerificationStatus verificationStatus,
        @NotNull Boolean inActiveGame,
        Instant lastCheckedAt) {
}
