package org.dariusturcu.backend.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class PipelineRetentionService {

    private final PipelineJobRepository pipelineJobRepository;
    private final int pipelineRetentionDays;

    public PipelineRetentionService(
            PipelineJobRepository pipelineJobRepository,
            @Value("${retention.pipeline-jobs-days:30}") int pipelineRetentionDays
    ) {
        this.pipelineJobRepository = pipelineJobRepository;
        this.pipelineRetentionDays = pipelineRetentionDays;
    }

    @Transactional
    public int purgeTerminalPipelineRunsAndJobs() {
        Instant cutoff = Instant.now().minus(pipelineRetentionDays, ChronoUnit.DAYS);
        int purgedRuns = pipelineJobRepository.deleteTerminalRunsOlderThan(cutoff);
        int purgedJobs = pipelineJobRepository.deleteTerminalJobsOlderThan(cutoff);
        int purgedPlaylistImports = pipelineJobRepository.deleteTerminalPlaylistImportJobsOlderThan(cutoff);
        int totalPurged = purgedRuns + purgedJobs + purgedPlaylistImports;
        if (totalPurged > 0) {
            log.info("Purged {} terminal pipeline runs, {} terminal pipeline jobs, and {} terminal playlist import jobs older than {} days",
                    purgedRuns, purgedJobs, purgedPlaylistImports, pipelineRetentionDays);
        }
        return totalPurged;
    }
}
