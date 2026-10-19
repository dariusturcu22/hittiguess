package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.pipeline.StageDependencies.StageJobState;
import org.dariusturcu.backend.pipeline.StageDependencies.StageTransition;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StageDependenciesTest {

    private static final int AGREED_YEAR = 1984;
    private static final int OTHER_YEAR = 1985;
    private static final StageJobState DONE_WITHOUT_YEAR = new StageJobState(PipelineJobStatus.DONE, null);
    private static final StageJobState WAITING = new StageJobState(PipelineJobStatus.WAITING, null);
    private static final StageJobState IN_PROGRESS = new StageJobState(PipelineJobStatus.IN_PROGRESS, null);
    private static final StageJobState SKIPPED = new StageJobState(PipelineJobStatus.SKIPPED, null);
    private static final StageJobState FAILED = new StageJobState(PipelineJobStatus.FAILED, null);

    private static StageJobState doneWithYear(int earliestYear) {
        return new StageJobState(PipelineJobStatus.DONE, earliestYear);
    }

    private static Map<PipelineStage, StageJobState> afterMetadata() {
        Map<PipelineStage, StageJobState> jobs = new EnumMap<>(PipelineStage.class);
        jobs.put(PipelineStage.YOUTUBE_METADATA, DONE_WITHOUT_YEAR);
        return jobs;
    }

    private static Map<PipelineStage, StageJobState> withSources(StageJobState musicBrainz, StageJobState discogs,
                                                                StageJobState wikidata) {
        Map<PipelineStage, StageJobState> jobs = afterMetadata();
        jobs.put(PipelineStage.MUSICBRAINZ, musicBrainz);
        jobs.put(PipelineStage.DISCOGS, discogs);
        jobs.put(PipelineStage.WIKIDATA, wikidata);
        return jobs;
    }

    private static final StageTransition SKIP_TO_SAVE = new StageTransition(Set.of(),
            Set.of(PipelineStage.WIKIPEDIA, PipelineStage.LLM_RECONCILIATION), true, false);
    private static final StageTransition QUEUE_WIKIPEDIA =
            new StageTransition(Set.of(PipelineStage.WIKIPEDIA), Set.of(), false, false);
    private static final StageTransition NOTHING = new StageTransition(Set.of(), Set.of(), false, false);

    @Test
    void finishedMetadataQueuesTheThreeSourcesTogether() {
        assertThat(StageDependencies.afterStageFinished(PipelineStage.YOUTUBE_METADATA, afterMetadata()))
                .isEqualTo(new StageTransition(
                        Set.of(PipelineStage.MUSICBRAINZ, PipelineStage.DISCOGS, PipelineStage.WIKIDATA),
                        Set.of(), false, false));
    }

    @Test
    void finishedMetadataDoesNotRecreateSourcesThatAlreadyExist() {
        Map<PipelineStage, StageJobState> jobs = withSources(WAITING, WAITING, WAITING);

        assertThat(StageDependencies.afterStageFinished(PipelineStage.YOUTUBE_METADATA, jobs)).isEqualTo(NOTHING);
    }

    @Test
    void threeAgreeingYearsSkipWikipediaAndReconciliationWithoutAnEmbeddingJob() {
        Map<PipelineStage, StageJobState> jobs =
                withSources(doneWithYear(AGREED_YEAR), doneWithYear(AGREED_YEAR), doneWithYear(AGREED_YEAR));

        assertThat(StageDependencies.afterStageFinished(PipelineStage.WIKIDATA, jobs)).isEqualTo(SKIP_TO_SAVE);
    }

    @Test
    void twoAgreeingAndOneDifferentYearQueueWikipedia() {
        Map<PipelineStage, StageJobState> jobs =
                withSources(doneWithYear(AGREED_YEAR), doneWithYear(OTHER_YEAR), doneWithYear(AGREED_YEAR));

        assertThat(StageDependencies.afterStageFinished(PipelineStage.DISCOGS, jobs)).isEqualTo(QUEUE_WIKIPEDIA);
    }

    @Test
    void aSourceWithoutAYearQueuesWikipedia() {
        Map<PipelineStage, StageJobState> jobs =
                withSources(doneWithYear(AGREED_YEAR), DONE_WITHOUT_YEAR, doneWithYear(AGREED_YEAR));

        assertThat(StageDependencies.afterStageFinished(PipelineStage.MUSICBRAINZ, jobs)).isEqualTo(QUEUE_WIKIPEDIA);
    }

    @Test
    void aSkippedSourceQueuesWikipedia() {
        Map<PipelineStage, StageJobState> jobs =
                withSources(doneWithYear(AGREED_YEAR), SKIPPED, doneWithYear(AGREED_YEAR));

        assertThat(StageDependencies.afterStageFinished(PipelineStage.MUSICBRAINZ, jobs)).isEqualTo(QUEUE_WIKIPEDIA);
    }

    @Test
    void aFailedSourceCountsAsFinishedAndQueuesWikipedia() {
        Map<PipelineStage, StageJobState> jobs =
                withSources(FAILED, doneWithYear(AGREED_YEAR), doneWithYear(AGREED_YEAR));

        assertThat(StageDependencies.afterStageFinished(PipelineStage.WIKIDATA, jobs)).isEqualTo(QUEUE_WIKIPEDIA);
    }

    @Test
    void nothingHappensWhileAnotherSourceIsStillWaitingOrInProgress() {
        Map<PipelineStage, StageJobState> waitingDiscogs =
                withSources(doneWithYear(AGREED_YEAR), WAITING, doneWithYear(AGREED_YEAR));
        Map<PipelineStage, StageJobState> runningWikidata =
                withSources(doneWithYear(AGREED_YEAR), doneWithYear(AGREED_YEAR), IN_PROGRESS);

        assertThat(StageDependencies.afterStageFinished(PipelineStage.MUSICBRAINZ, waitingDiscogs)).isEqualTo(NOTHING);
        assertThat(StageDependencies.afterStageFinished(PipelineStage.MUSICBRAINZ, runningWikidata)).isEqualTo(NOTHING);
    }

    @Test
    void theDecisionIsNotMadeTwiceOnceWikipediaExists() {
        Map<PipelineStage, StageJobState> jobs =
                withSources(doneWithYear(AGREED_YEAR), doneWithYear(OTHER_YEAR), doneWithYear(AGREED_YEAR));
        jobs.put(PipelineStage.WIKIPEDIA, WAITING);

        assertThat(StageDependencies.afterStageFinished(PipelineStage.DISCOGS, jobs)).isEqualTo(NOTHING);
    }

    @Test
    void finishedWikipediaQueuesTheReconciliation() {
        Map<PipelineStage, StageJobState> jobs =
                withSources(doneWithYear(AGREED_YEAR), doneWithYear(OTHER_YEAR), doneWithYear(AGREED_YEAR));
        jobs.put(PipelineStage.WIKIPEDIA, DONE_WITHOUT_YEAR);

        assertThat(StageDependencies.afterStageFinished(PipelineStage.WIKIPEDIA, jobs))
                .isEqualTo(new StageTransition(Set.of(PipelineStage.LLM_RECONCILIATION), Set.of(), false, false));
    }

    @Test
    void finishedReconciliationMakesTheRunReadyToSaveWithoutAnEmbeddingJob() {
        Map<PipelineStage, StageJobState> jobs =
                withSources(doneWithYear(AGREED_YEAR), doneWithYear(OTHER_YEAR), doneWithYear(AGREED_YEAR));
        jobs.put(PipelineStage.WIKIPEDIA, DONE_WITHOUT_YEAR);
        jobs.put(PipelineStage.LLM_RECONCILIATION, DONE_WITHOUT_YEAR);

        assertThat(StageDependencies.afterStageFinished(PipelineStage.LLM_RECONCILIATION, jobs))
                .isEqualTo(new StageTransition(Set.of(), Set.of(), true, false));
    }

    @Test
    void finishedEmbeddingEndsTheRun() {
        Map<PipelineStage, StageJobState> jobs =
                withSources(doneWithYear(AGREED_YEAR), doneWithYear(AGREED_YEAR), doneWithYear(AGREED_YEAR));
        jobs.put(PipelineStage.WIKIPEDIA, SKIPPED);
        jobs.put(PipelineStage.LLM_RECONCILIATION, SKIPPED);
        jobs.put(PipelineStage.EMBEDDING, DONE_WITHOUT_YEAR);

        assertThat(StageDependencies.afterStageFinished(PipelineStage.EMBEDDING, jobs))
                .isEqualTo(new StageTransition(Set.of(), Set.of(), false, true));
    }
}
