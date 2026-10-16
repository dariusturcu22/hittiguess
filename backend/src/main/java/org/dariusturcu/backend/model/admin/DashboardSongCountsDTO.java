package org.dariusturcu.backend.model.admin;

import jakarta.validation.constraints.NotNull;

public record DashboardSongCountsDTO(
        @NotNull Long verified,
        @NotNull Long needsReview,
        @NotNull Long inQueue,
        @NotNull Long processing,
        @NotNull Long error,
        @NotNull Long total) {
}
