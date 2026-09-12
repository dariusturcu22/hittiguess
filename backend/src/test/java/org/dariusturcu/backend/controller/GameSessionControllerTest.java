package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.model.mapper.SessionMapper;
import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.GameSessionDTO;
import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GameSessionService;
import org.dariusturcu.backend.service.SessionResultsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GameSessionControllerTest {

    @Mock
    private GameSessionService gameSessionService;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private SessionResultsStore resultsStore;

    private MockMvc mockMvc;

    private static final Long SESSION_ID = 1L;
    private static final Long GROUP_ID = 2L;
    private static final Long PLAYER_USER_ID = 10L;
    private static final Long OUTSIDER_USER_ID = 99L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GameSessionController(gameSessionService, sessionMapper, resultsStore))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("user-" + userId);
        user.setRole(Role.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    private GameSession sessionWithOnePlayer() {
        User playerUser = new User();
        playerUser.setId(PLAYER_USER_ID);

        Player player = new Player();
        player.setUser(playerUser);

        GameSession session = new GameSession();
        session.setId(SESSION_ID);
        session.setPlayers(List.of(player));
        return session;
    }

    @Test
    void getSessionReturnsTheSessionForAPlayerInIt() throws Exception {
        authenticateAs(PLAYER_USER_ID);
        GameSession session = sessionWithOnePlayer();
        when(gameSessionService.getSession(SESSION_ID)).thenReturn(session);
        when(gameSessionService.getCurrentRoundOrNull(session)).thenReturn(null);
        when(sessionMapper.toSessionDTO(any(), any())).thenReturn(
                new GameSessionDTO(SESSION_ID, GROUP_ID, null, null, 10, 1, List.of(), null));

        mockMvc.perform(get("/api/sessions/{sessionId}", SESSION_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getSessionRejectsAUserWhoIsNotAPlayerInTheSession() throws Exception {
        authenticateAs(OUTSIDER_USER_ID);
        when(gameSessionService.getSession(SESSION_ID)).thenReturn(sessionWithOnePlayer());

        mockMvc.perform(get("/api/sessions/{sessionId}", SESSION_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getResultsReturnsNotFoundWhenNoCompletedSessionExistsForTheGroup() throws Exception {
        when(resultsStore.get(GROUP_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sessions/groups/{groupId}/results", GROUP_ID))
                .andExpect(status().isNotFound());
    }
}
