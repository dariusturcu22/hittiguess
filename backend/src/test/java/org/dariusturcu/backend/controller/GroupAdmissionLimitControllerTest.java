package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.service.GameSessionAdmissionGuard;
import org.dariusturcu.backend.service.GameSessionService;
import org.dariusturcu.backend.service.GroupService;
import org.dariusturcu.backend.service.TurnCredentialsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GroupAdmissionLimitControllerTest {

    private static final Long TEST_GROUP_ID = 42L;

    @Mock
    private GroupService groupService;
    @Mock
    private GameSessionService gameSessionService;
    @Mock
    private TurnCredentialsService turnCredentialsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GroupController controller = new GroupController(groupService, gameSessionService, turnCredentialsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void startSessionReturnsServiceUnavailableWhenAdmissionLimitIsReached() throws Exception {
        when(gameSessionService.beginAudioCheck(TEST_GROUP_ID))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        GameSessionAdmissionGuard.CAPACITY_EXCEEDED_MESSAGE));

        mockMvc.perform(post("/api/groups/{groupId}/start-session", TEST_GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(HttpStatus.SERVICE_UNAVAILABLE.value()))
                .andExpect(jsonPath("$.message").value(GameSessionAdmissionGuard.CAPACITY_EXCEEDED_MESSAGE));
    }

    @Test
    void startAfterAudioCheckReturnsServiceUnavailableWhenAdmissionLimitIsReached() throws Exception {
        when(groupService.startGameAfterAudioCheck(TEST_GROUP_ID))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        GameSessionAdmissionGuard.CAPACITY_EXCEEDED_MESSAGE));

        mockMvc.perform(post("/api/groups/{groupId}/audio-check/start", TEST_GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(HttpStatus.SERVICE_UNAVAILABLE.value()))
                .andExpect(jsonPath("$.message").value(GameSessionAdmissionGuard.CAPACITY_EXCEEDED_MESSAGE));
    }
}
