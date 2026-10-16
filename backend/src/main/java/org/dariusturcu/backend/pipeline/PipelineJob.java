package org.dariusturcu.backend.pipeline;

import java.time.Instant;

public record PipelineJob(
        long id,
        long runId,
        PipelineStage stage,
        PipelineLane lane,
        PipelineJobStatus status,
        int attempts,
        int maxAttempts,
        int priority,
        Instant nextAttemptAt,
        String leaseOwner,
        Instant leaseExpiresAt,
        String resultJson,
        FailureKind failureKind,
        PipelineStage failureStage,
        String failureReason,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Instant updatedAt) {
}
