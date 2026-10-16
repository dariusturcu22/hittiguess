package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

// One backlog row for a song's YouTube ID, as the admin song detail lists it.
public record AdminQueueHistoryEntryDTO(
        @NotNull Long id,
        @NotNull PendingImportOrigin origin,
        @NotNull PendingImportStatus status,
        @NotNull Instant enqueuedAt,
        Instant processedAt,
        Integer provisionalYear,
        Integer patientYear,
        String failureReason) {
}
