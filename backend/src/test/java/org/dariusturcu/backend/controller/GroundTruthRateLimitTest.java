package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.ratelimit.RateLimitingFilter;
import org.dariusturcu.backend.service.GroundTruthService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the public ground-truth endpoint shares the general anonymous rate
 * limit: 60 requests in the window succeed, the 61st is rejected with 429.
 * A standalone MockMvc setup with the real filter and a mocked service keeps
 * the test on the limiter and the wiring, needing no database.
 */
class GroundTruthRateLimitTest {

    private static final int GENERAL_MAX_REQUESTS_PER_WINDOW = 60;
    private static final String GROUND_TRUTH_SONGS_PATH = "/api/ground-truth/songs";

    private MockMvc mockMvc() {
        GroundTruthService groundTruthService = mock(GroundTruthService.class);
        when(groundTruthService.verifiedSongs(any())).thenReturn(new PageImpl<>(List.of()));
        return MockMvcBuilders
                .standaloneSetup(new GroundTruthController(groundTruthService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .addFilters(new RateLimitingFilter(new ObjectMapper()))
                .build();
    }

    @Test
    void anonymousRequestsShareTheGeneralBucketUpToItsBoundary() throws Exception {
        MockMvc mockMvc = mockMvc();
        for (int requestIndex = 0; requestIndex < GENERAL_MAX_REQUESTS_PER_WINDOW; requestIndex++) {
            mockMvc.perform(get(GROUND_TRUTH_SONGS_PATH)).andExpect(status().isOk());
        }
        mockMvc.perform(get(GROUND_TRUTH_SONGS_PATH)).andExpect(status().isTooManyRequests());
    }
}
