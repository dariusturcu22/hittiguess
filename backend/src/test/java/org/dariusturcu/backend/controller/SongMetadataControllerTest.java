package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.service.SongMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SongMetadataControllerTest {

    @Mock
    private SongMetadataService songMetadataService;

    private MockMvc mockMvc;

    private static final String YOUTUBE_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SongMetadataController(songMetadataService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getSongMetadataReturnsTheAiServiceResponse() throws Exception {
        when(songMetadataService.fetchMetadata(YOUTUBE_URL))
                .thenReturn(new AiResponse(null, "some-model", 100L, LocalDateTime.now(), "SUCCESS"));

        mockMvc.perform(get("/api/metadata/song").param("youtubeUrl", YOUTUBE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void getSongMetadataOnRateLimitFallsThroughTheCatchAllRuntimeExceptionHandler() throws Exception {
        // GlobalExceptionHandler's RuntimeException handler matches before Spring's own
        // ResponseStatusException resolver runs, so the 429 the service raises here never
        // reaches the client; the response is always 400. Tracked as a known gap, not this
        // story's to fix.
        when(songMetadataService.fetchMetadata(YOUTUBE_URL))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "A metadata request is already in progress"));

        mockMvc.perform(get("/api/metadata/song").param("youtubeUrl", YOUTUBE_URL))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSongMetadataRejectsARequestMissingTheYoutubeUrlParameter() throws Exception {
        mockMvc.perform(get("/api/metadata/song"))
                .andExpect(status().isBadRequest());
    }
}
