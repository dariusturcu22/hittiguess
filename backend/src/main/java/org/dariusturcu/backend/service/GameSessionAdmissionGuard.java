package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.session.SessionStatus;
import org.dariusturcu.backend.repository.GameSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GameSessionAdmissionGuard {

    public static final String CAPACITY_EXCEEDED_MESSAGE =
            "Server is at maximum game capacity. Please try again shortly.";

    private final GameSessionRepository gameSessionRepository;
    private final int maxActiveGames;

    public GameSessionAdmissionGuard(
            GameSessionRepository gameSessionRepository,
            @Value("${game.session.max-active-games:50}") int maxActiveGames) {
        this.gameSessionRepository = gameSessionRepository;
        this.maxActiveGames = maxActiveGames;
    }

    public void requireAdmissionCapacity() {
        if (maxActiveGames <= 0) {
            return;
        }
        long activeGameCount = gameSessionRepository.countByStatus(SessionStatus.IN_PROGRESS);
        if (activeGameCount >= maxActiveGames) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, CAPACITY_EXCEEDED_MESSAGE);
        }
    }

    public int getMaxActiveGames() {
        return maxActiveGames;
    }
}
