package org.dariusturcu.backend.model.admin;

import jakarta.validation.constraints.NotNull;
import org.dariusturcu.backend.model.usage.AdminUsageDTO;

/**
 * The admin dashboard: catalog counts without any filter, queue totals across every origin, and
 * the same usage view the usage endpoint returns.
 */
public record AdminDashboardDTO(
        @NotNull DashboardSongCountsDTO songs,
        @NotNull DashboardQueueDTO queue,
        @NotNull AdminUsageDTO usage,
        QueueOverviewDTO pipeline) {
    public AdminDashboardDTO(DashboardSongCountsDTO songs, DashboardQueueDTO queue, AdminUsageDTO usage) {
        this(songs, queue, usage, null);
    }
}
