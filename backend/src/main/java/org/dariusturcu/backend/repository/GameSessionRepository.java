package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findByGroupId(Long groupId);

    List<GameSession> findByStatus(SessionStatus status);

    long countByStatus(SessionStatus status);

    List<GameSession> findByStatusAndZeroConnectedSinceBefore(SessionStatus status, Instant instant);

    // Takes the session's row lock without loading the entity, so state read after it in the
    // same transaction is what the previous lock holder committed.
    @Query(value = "SELECT id FROM game_sessions WHERE id = :sessionId FOR UPDATE", nativeQuery = true)
    Optional<Long> lockById(@Param("sessionId") Long sessionId);
}
