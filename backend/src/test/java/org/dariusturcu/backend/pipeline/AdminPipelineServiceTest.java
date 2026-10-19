package org.dariusturcu.backend.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminPipelineServiceTest {
    @Test
    void routeStageNamesAndRetryFiltersHaveExplicitValidation() {
        assertThat(AdminPipelineService.stage("youtube-metadata")).isEqualTo(PipelineStage.YOUTUBE_METADATA);
        assertThat(AdminPipelineService.status("retrying")).isEqualTo(AdminPipelineService.RETRYING_FILTER);
        assertThatThrownBy(() -> AdminPipelineService.stage("unknown")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdminPipelineService.status("unknown")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdminPipelineService.page(-1)).isInstanceOf(IllegalArgumentException.class);
    }

}
