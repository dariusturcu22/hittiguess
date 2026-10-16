package org.dariusturcu.backend.model.admin;

import jakarta.validation.constraints.NotNull;

// Waiting is PENDING queue rows and inProgress is PROCESSING rows, across every origin.
public record DashboardQueueDTO(
        @NotNull Long waiting,
        @NotNull Long inProgress) {
}
