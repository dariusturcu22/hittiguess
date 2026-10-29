package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.NotNull;

// Catalog row counts per status for the filter chips. They follow the search text and
// ignore the status filter.
public record AdminCatalogCountsDTO(
        @NotNull Long all,
        @NotNull Long verified,
        @NotNull Long needsReview,
        @NotNull Long inQueue,
        @NotNull Long processing,
        @NotNull Long error,
        Long proposedYear) {
    public AdminCatalogCountsDTO(Long all, Long verified, Long needsReview, Long inQueue, Long processing, Long error) {
        this(all, verified, needsReview, inQueue, processing, error, 0L);
    }
}
