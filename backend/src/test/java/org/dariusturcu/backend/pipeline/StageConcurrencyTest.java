package org.dariusturcu.backend.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageConcurrencyTest {
    private static final int DEFAULT_METADATA_SLOTS = 15;
    private static final int CONFIGURED_YOUTUBE_SLOTS = 5;
    private static final int CONFIGURED_RECONCILIATION_SLOTS = 7;
    private static final int INVALID_LIMIT = 0;

    @Test
    void defaultsAllocateSlotsForEveryMetadataStage() {
        StageConcurrency concurrency = StageConcurrency.defaults();

        assertThat(concurrency.limit(PipelineStage.YOUTUBE_METADATA))
                .isEqualTo(StageConcurrency.DEFAULT_YOUTUBE_METADATA_LIMIT);
        assertThat(concurrency.limit(PipelineStage.LLM_RECONCILIATION))
                .isEqualTo(StageConcurrency.DEFAULT_LLM_RECONCILIATION_LIMIT);
        for (PipelineStage stage : java.util.List.of(PipelineStage.MUSICBRAINZ, PipelineStage.DISCOGS,
                PipelineStage.WIKIDATA, PipelineStage.WIKIPEDIA)) {
            assertThat(concurrency.limit(stage)).isEqualTo(StageConcurrency.DEFAULT_SOURCE_LIMIT);
        }
        assertThat(concurrency.totalMetadataLimit()).isEqualTo(DEFAULT_METADATA_SLOTS);
    }

    @Test
    void hyphenatedStagePropertiesOverrideOnlyTheirStage() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("pipeline.stages.concurrency.youtube-metadata", CONFIGURED_YOUTUBE_SLOTS)
                .withProperty("pipeline.stages.concurrency.llm-reconciliation", CONFIGURED_RECONCILIATION_SLOTS);
        StageConcurrency concurrency = new StageConcurrency(environment);

        assertThat(concurrency.limit(PipelineStage.YOUTUBE_METADATA)).isEqualTo(CONFIGURED_YOUTUBE_SLOTS);
        assertThat(concurrency.limit(PipelineStage.LLM_RECONCILIATION)).isEqualTo(CONFIGURED_RECONCILIATION_SLOTS);
        assertThat(concurrency.limit(PipelineStage.MUSICBRAINZ)).isEqualTo(StageConcurrency.DEFAULT_SOURCE_LIMIT);
        assertThat(concurrency.totalMetadataLimit()).isEqualTo(DEFAULT_METADATA_SLOTS
                - StageConcurrency.DEFAULT_YOUTUBE_METADATA_LIMIT - StageConcurrency.DEFAULT_LLM_RECONCILIATION_LIMIT
                + CONFIGURED_YOUTUBE_SLOTS + CONFIGURED_RECONCILIATION_SLOTS);
    }

    @Test
    void aNonpositiveLimitCannotDisableAStageSilently() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("pipeline.stages.concurrency.musicbrainz", INVALID_LIMIT);

        assertThatThrownBy(() -> new StageConcurrency(environment)).isInstanceOf(IllegalArgumentException.class);
    }
}
