package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.session.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, Long> {

    Optional<Round> findTopBySessionOrderByRoundNumberDesc(GameSession session);

    List<Round> findBySessionOrderByRoundNumberAsc(GameSession session);

    // The concurrency-safe core of betting: a single conditional UPDATE, guarded by
    // "bettor_player_id IS NULL" in the WHERE clause. Postgres serializes concurrent
    // UPDATEs against the same row, so of any number of simultaneous callers exactly one
    // update affects a row (returns 1) and every other one affects zero rows, with no
    // explicit locking or retry needed on the application side. See DECISIONS.md.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Round round SET round.bettorPlayer = :player, round.betPlacedAt = :now "
            + "WHERE round.id = :roundId AND round.bettorPlayer IS NULL")
    int tryAcceptBet(@Param("roundId") Long roundId, @Param("player") Player player, @Param("now") Instant now);
}
