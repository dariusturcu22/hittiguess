package org.dariusturcu.backend.model.admin;

import org.dariusturcu.backend.pipeline.ImportBatchKind;
import java.time.Instant;

public record ImportBatchDTO(long id, ImportBatchKind kind, String label, String sourceLink, Instant createdAt,
        Instant cancelledAt, long total, long waiting, long inProgress, long done, long failed, long cancelled,
        int skippedDuplicates, Long estimatedDrainSeconds) {
}
