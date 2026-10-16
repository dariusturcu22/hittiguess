package org.dariusturcu.backend.pipeline;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The stage graph of one run. YouTube metadata fans out to MusicBrainz, Discogs, and Wikidata.
 * Once all three are terminal, exactly agreeing earliest years skip Wikipedia and the LLM
 * reconciliation; any disagreement, missing year, or source that did not finish queues
 * Wikipedia, then the reconciliation. Finishing the reconciliation, or skipping it, makes the
 * run ready to save and queues its embedding; the finished embedding ends the run.
 */
public final class StageDependencies {

    static final List<PipelineStage> SOURCE_STAGES =
            List.of(PipelineStage.MUSICBRAINZ, PipelineStage.DISCOGS, PipelineStage.WIKIDATA);
    private static final long SINGLE_DISTINCT_YEAR = 1;

    private StageDependencies() {
    }

    /** One existing job of the run: its status and the earliest year its result reported. */
    public record StageJobState(PipelineJobStatus status, Integer earliestYear) {
    }

    public record StageTransition(Set<PipelineStage> stagesToCreate, Set<PipelineStage> stagesToSkip,
                                  boolean readyToSave, boolean runDone) {

        static final StageTransition NOTHING = new StageTransition(Set.of(), Set.of(), false, false);
        static final StageTransition RUN_DONE = new StageTransition(Set.of(), Set.of(), false, true);

        static StageTransition create(Set<PipelineStage> stages) {
            return new StageTransition(stages, Set.of(), false, false);
        }
    }

    /**
     * What follows the given stage finishing, given every job the run has so far (the finished
     * one included). A stage that already has a job is never created or skipped again.
     */
    public static StageTransition afterStageFinished(PipelineStage finishedStage,
                                                     Map<PipelineStage, StageJobState> jobsByStage) {
        return switch (finishedStage) {
            case YOUTUBE_METADATA -> StageTransition.create(missingStages(SOURCE_STAGES, jobsByStage));
            case MUSICBRAINZ, DISCOGS, WIKIDATA -> afterSourceStage(jobsByStage);
            case WIKIPEDIA -> StageTransition.create(
                    missingStages(List.of(PipelineStage.LLM_RECONCILIATION), jobsByStage));
            case LLM_RECONCILIATION -> readyToSave(Set.of(), jobsByStage);
            case EMBEDDING -> StageTransition.RUN_DONE;
        };
    }

    private static StageTransition afterSourceStage(Map<PipelineStage, StageJobState> jobsByStage) {
        boolean everySourceTerminal = SOURCE_STAGES.stream()
                .allMatch(stage -> jobsByStage.containsKey(stage) && jobsByStage.get(stage).status().isTerminal());
        boolean decisionAlreadyMade = jobsByStage.containsKey(PipelineStage.WIKIPEDIA)
                || jobsByStage.containsKey(PipelineStage.LLM_RECONCILIATION);
        if (!everySourceTerminal || decisionAlreadyMade) {
            return StageTransition.NOTHING;
        }
        if (sourcesAgree(jobsByStage)) {
            return readyToSave(EnumSet.of(PipelineStage.WIKIPEDIA, PipelineStage.LLM_RECONCILIATION), jobsByStage);
        }
        return StageTransition.create(EnumSet.of(PipelineStage.WIKIPEDIA));
    }

    private static boolean sourcesAgree(Map<PipelineStage, StageJobState> jobsByStage) {
        List<StageJobState> sourceStates = SOURCE_STAGES.stream().map(jobsByStage::get).toList();
        boolean everySourceAnsweredWithYear = sourceStates.stream()
                .allMatch(state -> state.status() == PipelineJobStatus.DONE && state.earliestYear() != null);
        return everySourceAnsweredWithYear
                && sourceStates.stream().map(StageJobState::earliestYear).distinct().count() == SINGLE_DISTINCT_YEAR;
    }

    private static StageTransition readyToSave(Set<PipelineStage> stagesToSkip,
                                               Map<PipelineStage, StageJobState> jobsByStage) {
        return new StageTransition(missingStages(List.of(PipelineStage.EMBEDDING), jobsByStage),
                stagesToSkip, true, false);
    }

    private static Set<PipelineStage> missingStages(List<PipelineStage> stages,
                                                    Map<PipelineStage, StageJobState> jobsByStage) {
        Set<PipelineStage> missing = EnumSet.noneOf(PipelineStage.class);
        stages.stream().filter(stage -> !jobsByStage.containsKey(stage)).forEach(missing::add);
        return missing;
    }
}
