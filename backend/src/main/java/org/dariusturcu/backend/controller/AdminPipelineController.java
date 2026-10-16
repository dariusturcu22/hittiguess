package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dariusturcu.backend.model.admin.*;
import org.dariusturcu.backend.model.song.AdminCatalogSeedingRequest;
import org.dariusturcu.backend.model.song.EnqueueResultDTO;
import org.dariusturcu.backend.pipeline.AdminPipelineService;
import org.dariusturcu.backend.pipeline.PipelineLane;
import org.dariusturcu.backend.pipeline.PipelineRunStatus;
import org.dariusturcu.backend.pipeline.RunQueueState;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin pipeline", description = "Admin-only stage queues, batches, and pipeline controls")
public class AdminPipelineController {
    private final AdminPipelineService pipeline;
    private final AdminAccessGuard access;

    public AdminPipelineController(AdminPipelineService pipeline, AdminAccessGuard access) {
        this.pipeline = pipeline;
        this.access = access;
    }

    @GetMapping("/queues")
    @Operation(summary = "Stage queues, counts, pace, cooldowns, estimates, and the YouTube reserve")
    public QueueOverviewDTO queues() { access.requireAdmin(); return pipeline.queues(); }

    @GetMapping("/queues/{stage}/jobs")
    @Operation(summary = "Jobs of one stage, filtered by status including RETRYING, 25 per page")
    public QueueJobPageDTO jobs(@PathVariable String stage, @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page) {
        access.requireAdmin(); return pipeline.jobs(stage, status, page);
    }

    @GetMapping("/runs")
    @Operation(summary = "Pipeline songs with their stage progress, filtered by run status and lane, 25 per page")
    public PipelineRunPageDTO runs(@RequestParam(required = false) String status,
            @RequestParam(required = false) String lane, @RequestParam(required = false) String state,
            @RequestParam(required = false) Integer page) {
        access.requireAdmin();
        return pipeline.runs(parse(PipelineRunStatus.class, status), parse(PipelineLane.class, lane), parse(RunQueueState.class, state), page);
    }

    @PostMapping("/queues/{stage}/pause")
    @Operation(summary = "Pause a stage after its current job finishes")
    public PipelineControlResultDTO pauseStage(@PathVariable String stage) { access.requireAdmin(); return pipeline.pause(stage, true); }

    @PostMapping("/queues/{stage}/resume")
    @Operation(summary = "Resume a paused stage")
    public PipelineControlResultDTO resumeStage(@PathVariable String stage) { access.requireAdmin(); return pipeline.pause(stage, false); }

    @PostMapping("/pipeline/pause")
    @Operation(summary = "Pause all stage queues")
    public PipelineControlResultDTO pausePipeline() { access.requireAdmin(); return pipeline.pauseAll(true); }

    @PostMapping("/pipeline/resume")
    @Operation(summary = "Resume all stage queues")
    public PipelineControlResultDTO resumePipeline() { access.requireAdmin(); return pipeline.pauseAll(false); }

    @PostMapping("/runs/{id}/retry")
    @Operation(summary = "Retry a failed pipeline run with fresh attempts")
    public PipelineControlResultDTO retryRun(@PathVariable long id) { access.requireAdmin(); return pipeline.retry(id); }

    @PostMapping("/runs/{id}/discard")
    @Operation(summary = "Discard an active or failed pipeline run")
    public PipelineControlResultDTO discardRun(@PathVariable long id) { access.requireAdmin(); return pipeline.discard(id); }

    @PostMapping("/runs/{id}/bump")
    @Operation(summary = "Raise a run above other waiting work in its lane")
    public PipelineControlResultDTO bumpRun(@PathVariable long id) { access.requireAdmin(); return pipeline.bump(id); }

    @PostMapping("/queues/retry-failed")
    @Operation(summary = "Retry all failed runs that have no other active run for the same video")
    public PipelineControlResultDTO retryFailedRuns(@RequestParam(required = false) String stage) {
        access.requireAdmin(); return pipeline.retryAll(stage);
    }

    @GetMapping("/batches")
    @Operation(summary = "Import batches with progress, skipped duplicates, and drain estimates, 25 per page")
    public ImportBatchPageDTO batches(@RequestParam(required = false) Integer page) { access.requireAdmin(); return pipeline.batches(page); }

    @PostMapping("/batches/{id}/cancel")
    @Operation(summary = "Cancel a batch and discard late results from jobs already running")
    public PipelineControlResultDTO cancelBatch(@PathVariable long id) { access.requireAdmin(); return pipeline.cancel(id); }

    @PostMapping("/pipeline/enqueue")
    @Operation(summary = "Add a playlist or video IDs to a batch and report skipped known or queued videos")
    public EnqueueResultDTO enqueuePipeline(@RequestBody AdminCatalogSeedingRequest request) {
        access.requireAdmin(); return pipeline.enqueue(request);
    }

    @PostMapping("/pipeline/run-now")
    @Operation(summary = "Queue one video at the highest backfill priority")
    public PipelineRunDTO runPipelineNow(@RequestBody RunNowRequest request) {
        access.requireAdmin(); return pipeline.runNow(request.youtubeId());
    }

    private static <EnumType extends Enum<EnumType>> EnumType parse(Class<EnumType> type, String value) {
        if (value == null || value.isBlank()) { return null; }
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Unknown " + type.getSimpleName()); }
    }
}
