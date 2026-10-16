package org.dariusturcu.backend.pipeline;

import java.util.Set;

public enum PipelineJobStatus {
    WAITING,
    IN_PROGRESS,
    DONE,
    FAILED,
    SKIPPED,
    CANCELLED;

    private static final Set<PipelineJobStatus> TERMINAL_STATUSES = Set.of(DONE, FAILED, SKIPPED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(this);
    }
}
