package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findByGroupId(Long groupId);

    List<GameSession> findByStatusAndZeroConnectedSinceBefore(SessionStatus status, Instant instant);
}
