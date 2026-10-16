package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.admin.ImportBatchDTO;
import org.dariusturcu.backend.model.admin.ImportBatchPageDTO;
import org.dariusturcu.backend.model.admin.PipelineControlResultDTO;
import org.dariusturcu.backend.model.admin.PipelineRunDTO;
import org.dariusturcu.backend.model.admin.PipelineRunPageDTO;
import org.dariusturcu.backend.model.admin.QueueJobDTO;
import org.dariusturcu.backend.model.admin.QueueJobPageDTO;
import org.dariusturcu.backend.model.admin.QueueOverviewDTO;
import org.dariusturcu.backend.model.admin.QueueStageDTO;
import org.dariusturcu.backend.model.admin.QueueState;
import org.dariusturcu.backend.model.song.AdminCatalogSeedingRequest;
import org.dariusturcu.backend.model.song.EnqueueResultDTO;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.service.ApiUsageService;
import org.dariusturcu.backend.service.CatalogSeedingService;
import org.dariusturcu.backend.util.YoutubeLinkParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminPipelineService {
    public static final int PAGE_SIZE = 25;
    public static final int FIRST_PAGE = 0;
    public static final String DONE_TODAY_FILTER = "DONE_TODAY";
    public static final String RETRYING_FILTER = "RETRYING";
    private static final String ADMIN_PAUSE_REASON = "Paused by an admin";

    private final AdminPipelineRepository repository;
    private final PipelineJobService engine;
    private final PipelineSettings settings;
    private final ApiUsageService usage;
    private final CatalogSeedingService seeding;
    private final StageConcurrency concurrency;

    public AdminPipelineService(AdminPipelineRepository repository, PipelineJobService engine, PipelineSettings settings,
            ApiUsageService usage, CatalogSeedingService seeding) {
        this(repository, engine, settings, usage, seeding, StageConcurrency.defaults());
    }

    @Autowired
    public AdminPipelineService(AdminPipelineRepository repository, PipelineJobService engine, PipelineSettings settings,
            ApiUsageService usage, CatalogSeedingService seeding, StageConcurrency concurrency) {
        this.repository = repository;
        this.engine = engine;
        this.settings = settings;
        this.usage = usage;
        this.seeding = seeding;
        this.concurrency = concurrency;
    }

    public QueueOverviewDTO queues() {
        Map<PipelineStage, StageCounts> counts = engine.stageCounts().stream().collect(Collectors.toMap(StageCounts::stage, Function.identity()));
        Map<PipelineStage, StageTimings> timings = engine.stageTimings();
        Instant now = Instant.now();
        List<DrainEstimate.StageEstimate> estimates = new ArrayList<>();
        List<QueueStageDTO> stages = repository.pace().stream().map(pace -> {
            StageCounts count = counts.get(pace.stage());
            StageTimings timing = timings.getOrDefault(pace.stage(), StageTimings.none(pace.stage()));
            QueueState state = pace.paused() ? QueueState.PAUSED
                    : pace.cooldownUntil() != null && pace.cooldownUntil().isAfter(now) ? QueueState.COOLING_DOWN : QueueState.RUNNING;
            long remainingJobs = count.waiting() + count.inProgress();
            Long drainSeconds = DrainEstimate.seconds(drainInputs(pace, remainingJobs, timing, now));
            estimates.add(new DrainEstimate.StageEstimate(remainingJobs, drainSeconds));
            return new QueueStageDTO(pace.stage(), state, count.waiting(), count.inProgress(), count.retrying(),
                    count.doneToday(), count.failed(), pace.delayMs(), pace.cooldownUntil(),
                    milliseconds(timing.averageDurationMs()), drainSeconds, pace.reason(), count.waitingDue(),
                    count.waiting() - count.waitingDue(), milliseconds(timing.averageQueueWaitMs()),
                    DrainEstimate.measuredJobsPerMinute(timing.finishedInThroughputWindow()), pace.sourceRequestMs(),
                    concurrency.limit(pace.stage()), timing.finishedInDurationWindow());
        }).toList();
        long remaining = usage.youtubeUnitsRemaining();
        return new QueueOverviewDTO(stages, settings.enabled(), remaining, settings.youtubeReserveUnits(),
                remaining < settings.youtubeReserveUnits(), DrainEstimate.overall(estimates));
    }

    private DrainEstimate.Inputs drainInputs(AdminPipelineRepository.Pace pace, long remainingJobs, StageTimings timing, Instant now) {
        Duration cooldown = pace.cooldownUntil() == null || !pace.cooldownUntil().isAfter(now)
                ? Duration.ZERO : Duration.between(now, pace.cooldownUntil());
        return new DrainEstimate.Inputs(remainingJobs, concurrency.limit(pace.stage()), timing.averageDurationMs(),
                timing.finishedInThroughputWindow(), pace.delayMs(), pace.paused(), cooldown);
    }

    private static Integer milliseconds(Long value) {
        return value == null ? null : Math.toIntExact(Math.min(value, Integer.MAX_VALUE));
    }

    public QueueJobPageDTO jobs(String stageName, String statusName, Integer page) {
        PipelineStage stage = stage(stageName);
        String status = status(statusName);
        int resolvedPage = page(page);
        long total = repository.jobCount(stage, status);
        return new QueueJobPageDTO(repository.jobs(stage, status, PAGE_SIZE, (long) resolvedPage * PAGE_SIZE), resolvedPage,
                PAGE_SIZE, total, pages(total));
    }

    public PipelineRunPageDTO runs(PipelineRunStatus status, PipelineLane lane, Integer page) {
        return runs(status, lane, null, page);
    }

    public PipelineRunPageDTO runs(PipelineRunStatus status, PipelineLane lane, RunQueueState state, Integer page) {
        int resolvedPage = page(page);
        Map<RunQueueState, Long> counts = repository.runCounts(status, lane);
        long total = state == null ? counts.values().stream().mapToLong(Long::longValue).sum() : counts.get(state);
        List<PipelineRunDTO> items = repository.runIds(status, lane, state, PAGE_SIZE, (long) resolvedPage * PAGE_SIZE).stream()
                .map(runId -> run(engine.findRun(runId).orElseThrow())).toList();
        return new PipelineRunPageDTO(items, resolvedPage, PAGE_SIZE, total, pages(total), counts);
    }

    public PipelineRunDTO run(PipelineRun run) {
        List<QueueJobDTO> jobs = repository.jobsOfRun(run.id());
        String title = jobs.stream().map(QueueJobDTO::title).filter(java.util.Objects::nonNull).findFirst().orElse(run.youtubeId());
        List<String> artists = jobs.stream().map(QueueJobDTO::artists).filter(names -> !names.isEmpty()).findFirst().orElse(List.of());
        return new PipelineRunDTO(run.id(), run.youtubeId(), title, run.lane(), run.status(), run.provisionalYear(), run.createdAt(), run.finishedAt(), jobs,
                run.batchId(), repository.batchLabel(run.batchId()), artists);
    }

    public ImportBatchPageDTO batches(Integer page) {
        int resolvedPage = page(page);
        long total = repository.batchCount();
        Map<PipelineStage, AdminPipelineRepository.Pace> pace = repository.pace().stream()
                .collect(Collectors.toMap(AdminPipelineRepository.Pace::stage, Function.identity()));
        Map<PipelineStage, StageTimings> timings = engine.stageTimings();
        Instant now = Instant.now();
        List<ImportBatchDTO> items = repository.batches(PAGE_SIZE, (long) resolvedPage * PAGE_SIZE).stream().map(batch -> {
            Long estimate = DrainEstimate.overall(repository.batchRemainingDepth(batch.id()).stream()
                    .map(depth -> new DrainEstimate.StageEstimate(depth.remaining(), DrainEstimate.seconds(drainInputs(
                            pace.get(depth.stage()), depth.remaining(),
                            timings.getOrDefault(depth.stage(), StageTimings.none(depth.stage())), now))))
                    .toList());
            return new ImportBatchDTO(batch.id(), batch.kind(), batch.label(), batch.sourceLink(), batch.createdAt(), batch.cancelledAt(),
                    batch.total(), batch.waiting(), batch.inProgress(), batch.done(), batch.failed(), batch.cancelled(), batch.skippedDuplicates(), estimate);
        }).toList();
        return new ImportBatchPageDTO(items, resolvedPage, PAGE_SIZE, total, pages(total));
    }

    @Transactional
    public PipelineControlResultDTO pause(String stageName, boolean paused) {
        PipelineStage stage = stage(stageName);
        engine.setStagePaused(stage, paused, paused ? ADMIN_PAUSE_REASON : null);
        return new PipelineControlResultDTO(1);
    }

    @Transactional
    public PipelineControlResultDTO pauseAll(boolean paused) {
        Arrays.stream(PipelineStage.values()).forEach(stage -> engine.setStagePaused(stage, paused, paused ? ADMIN_PAUSE_REASON : null));
        return new PipelineControlResultDTO(PipelineStage.values().length);
    }

    @Transactional
    public PipelineControlResultDTO retry(long runId) {
        requireRun(runId);
        if (engine.retryFailed(runId)) { return new PipelineControlResultDTO(1); }
        int waiting = engine.retryWaiting(runId);
        if (waiting == 0) { throw new ConflictException("The run cannot be retried in its current state"); }
        return new PipelineControlResultDTO(waiting);
    }

    @Transactional
    public PipelineControlResultDTO discard(long runId) {
        requireRun(runId);
        if (!engine.discardRun(runId)) { throw new ConflictException("The run is already finished"); }
        return new PipelineControlResultDTO(1);
    }

    @Transactional
    public PipelineControlResultDTO bump(long runId) {
        requireRun(runId);
        return new PipelineControlResultDTO(engine.bump(runId));
    }

    @Transactional
    public PipelineControlResultDTO retryAll() {
        return retryAll(null);
    }

    @Transactional
    public PipelineControlResultDTO retryAll(String stageName) {
        int retried = 0;
        PipelineStage stage = stageName == null ? null : stage(stageName);
        for (Long runId : repository.failedRunIds(stage)) { if (engine.retryFailed(runId)) { retried++; } }
        return new PipelineControlResultDTO(retried);
    }

    @Transactional
    public PipelineControlResultDTO cancel(long batchId) {
        if (!repository.batchExists(batchId)) { throw new ResourceNotFoundException(ResourceType.IMPORT_BATCH, batchId); }
        return new PipelineControlResultDTO(engine.cancelBatch(batchId));
    }

    @Transactional
    public EnqueueResultDTO enqueue(AdminCatalogSeedingRequest request) {
        requireEnabled();
        if (request == null || ((request.playlistLink() == null || request.playlistLink().isBlank())
                && (request.youtubeIds() == null || request.youtubeIds().isEmpty()))) {
            throw new IllegalArgumentException("A playlist link or video IDs are required");
        }
        if (request.youtubeIds() != null && request.youtubeIds().stream()
                .anyMatch(video -> video == null || YoutubeLinkParser.parseVideoId(video).isEmpty())) {
            throw new IllegalArgumentException("Every video entry must be a valid YouTube ID or link");
        }
        return seeding.enqueue(request);
    }

    @Transactional
    public PipelineRunDTO runNow(String youtubeId) {
        requireEnabled();
        String parsed = youtubeId == null ? null : YoutubeLinkParser.parseVideoId(youtubeId).orElse(null);
        if (parsed == null) { throw new IllegalArgumentException("A valid YouTube video ID or link is required"); }
        PipelineRun run = engine.startRun(parsed, PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_SEED, null, null);
        engine.bump(run.id());
        return run(run);
    }

    public void requireEnabled() {
        if (!settings.enabled()) { throw new ConflictException("The stage pipeline is disabled"); }
    }

    private PipelineRun requireRun(long runId) {
        return engine.findRun(runId).orElseThrow(() -> new ResourceNotFoundException(ResourceType.PIPELINE_RUN, runId));
    }

    static PipelineStage stage(String value) {
        try { return PipelineStage.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_')); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Unknown pipeline stage"); }
    }

    static String status(String value) {
        if (value == null || value.isBlank()) { return null; }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (RETRYING_FILTER.equals(normalized) || DONE_TODAY_FILTER.equals(normalized)) { return normalized; }
        try { return PipelineJobStatus.valueOf(normalized).name(); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Unknown job status"); }
    }

    static int page(Integer page) {
        if (page == null) { return FIRST_PAGE; }
        if (page < FIRST_PAGE) { throw new IllegalArgumentException("Page cannot be negative"); }
        return page;
    }

    private static int pages(long total) { return (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE); }

}
