package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.session.SessionStatus;
import org.dariusturcu.backend.repository.GameSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameSessionAdmissionGuardTest {

    private static final int CONFIGURED_MAXIMUM_ACTIVE_GAMES = 50;
    private static final long ACTIVE_GAMES_BELOW_LIMIT = 49L;
    private static final long ACTIVE_GAMES_AT_LIMIT = 50L;
    private static final long ACTIVE_GAMES_ABOVE_LIMIT = 55L;

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Test
    void allowsSessionWhenActiveGamesCountIsBelowLimit() {
        GameSessionAdmissionGuard admissionGuard =
                new GameSessionAdmissionGuard(gameSessionRepository, CONFIGURED_MAXIMUM_ACTIVE_GAMES);
        when(gameSessionRepository.countByStatus(SessionStatus.IN_PROGRESS))
                .thenReturn(ACTIVE_GAMES_BELOW_LIMIT);

        assertThatCode(admissionGuard::requireAdmissionCapacity)
                .doesNotThrowAnyException();
    }

    @Test
    void throwsServiceUnavailableWhenActiveGamesCountReachesLimit() {
        GameSessionAdmissionGuard admissionGuard =
                new GameSessionAdmissionGuard(gameSessionRepository, CONFIGURED_MAXIMUM_ACTIVE_GAMES);
        when(gameSessionRepository.countByStatus(SessionStatus.IN_PROGRESS))
                .thenReturn(ACTIVE_GAMES_AT_LIMIT);

        assertThatThrownBy(admissionGuard::requireAdmissionCapacity)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(thrownException -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) thrownException;
                    assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(responseStatusException.getReason())
                            .isEqualTo(GameSessionAdmissionGuard.CAPACITY_EXCEEDED_MESSAGE);
                });
    }

    @Test
    void throwsServiceUnavailableWhenActiveGamesCountExceedsLimit() {
        GameSessionAdmissionGuard admissionGuard =
                new GameSessionAdmissionGuard(gameSessionRepository, CONFIGURED_MAXIMUM_ACTIVE_GAMES);
        when(gameSessionRepository.countByStatus(SessionStatus.IN_PROGRESS))
                .thenReturn(ACTIVE_GAMES_ABOVE_LIMIT);

        assertThatThrownBy(admissionGuard::requireAdmissionCapacity)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(thrownException -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) thrownException;
                    assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(responseStatusException.getReason())
                            .isEqualTo(GameSessionAdmissionGuard.CAPACITY_EXCEEDED_MESSAGE);
                });
    }

    @Test
    void allowsSessionWhenAdmissionLimitIsDisabledWithZeroOrNegativeConfiguration() {
        int disabledLimit = 0;
        GameSessionAdmissionGuard admissionGuard =
                new GameSessionAdmissionGuard(gameSessionRepository, disabledLimit);

        assertThatCode(admissionGuard::requireAdmissionCapacity)
                .doesNotThrowAnyException();
    }
}
