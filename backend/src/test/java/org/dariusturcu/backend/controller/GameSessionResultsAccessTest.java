package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.model.mapper.SessionMapper;
import org.dariusturcu.backend.model.session.PlayerResultDTO;
import org.dariusturcu.backend.model.session.SessionResultsDTO;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.StoredSessionResultsRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GameSessionService;
import org.dariusturcu.backend.service.SessionResultsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class GameSessionResultsAccessTest {

    private static final Long GROUP_ID = 7L;
    private static final Long PLAYER_USER_ID = 11L;
    private static final Long OUTSIDER_USER_ID = 99L;
    private static final Long GROUP_WITHOUT_RESULTS_ID = 8L;

    @Mock
    private GameSessionService gameSessionService;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private StoredSessionResultsRepository storedSessionResultsRepository;

    private GameSessionController controller;
    private SessionResultsDTO results;

    @BeforeEach
    void setUp() {
        SessionResultsStore resultsStore = new SessionResultsStore(storedSessionResultsRepository, JsonMapper.builder().build());
        results = new SessionResultsDTO(GROUP_ID, List.of(new PlayerResultDTO(1L, "Winner", 5, 1)), List.of(), List.of());
        resultsStore.store(GROUP_ID, results, Set.of(PLAYER_USER_ID));
        controller = new GameSessionController(gameSessionService, sessionMapper, resultsStore);
    }

    @AfterEach
    void clearAuthentication() {
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

    @Test
    void aPlayerOfTheSessionCanReadItsResults() {
        authenticateAs(PLAYER_USER_ID);

        assertThat(controller.getResults(GROUP_ID).getBody()).isEqualTo(results);
    }

    @Test
    void someoneWhoDidntPlayIsRefused() {
        authenticateAs(OUTSIDER_USER_ID);

        assertThatThrownBy(() -> controller.getResults(GROUP_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aGroupWithNoCompletedSessionHasNoResults() {
        authenticateAs(PLAYER_USER_ID);

        assertThatThrownBy(() -> controller.getResults(GROUP_WITHOUT_RESULTS_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
