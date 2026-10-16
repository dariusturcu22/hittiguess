package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.exception.ConflictException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminPipelineServiceTest {
    private static final long WAITING_JOBS = 10;
    private static final int DELAY_MS = 1500;
    private static final int RESPONSE_MS = 250;
    private static final long ESTIMATE_SECONDS = 15;

    @Test
    void estimatesRespectTheSlowerOfPaceAndResponseTimeAndLeaveUnknownWorkUnknown() {
        assertThat(AdminPipelineService.estimate(WAITING_JOBS, DELAY_MS, RESPONSE_MS)).isEqualTo(ESTIMATE_SECONDS);
        assertThat(AdminPipelineService.estimate(WAITING_JOBS, RESPONSE_MS, DELAY_MS)).isEqualTo(ESTIMATE_SECONDS);
        assertThat(AdminPipelineService.estimate(WAITING_JOBS, null, null)).isNull();
        assertThat(AdminPipelineService.estimate(0, null, null)).isZero();
    }

    @Test
    void routeStageNamesAndRetryFiltersHaveExplicitValidation() {
        assertThat(AdminPipelineService.stage("youtube-metadata")).isEqualTo(PipelineStage.YOUTUBE_METADATA);
        assertThat(AdminPipelineService.status("retrying")).isEqualTo(AdminPipelineService.RETRYING_FILTER);
        assertThatThrownBy(() -> AdminPipelineService.stage("unknown")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdminPipelineService.status("unknown")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdminPipelineService.page(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabledStagePipelineCannotAcceptNewStageRuns() {
        var service = new AdminPipelineService(null, null,
                new PipelineSettings(false, PipelineSettings.DEFAULT_YOUTUBE_RESERVE_UNITS), null, null);
        assertThatThrownBy(() -> service.runNow("dQw4w9WgXcQ")).isInstanceOf(ConflictException.class);
    }
}
