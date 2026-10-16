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
        @NotNull Long error) {
}
