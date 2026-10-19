package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.session.Bet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface BetRepository extends JpaRepository<Bet, Long> {

    List<Bet> findByRoundId(Long roundId);

    // The player ids that already have an accepted bet on this round, used to keep an
    // already-bet player out of eligibleBettors without touching Round's own bets
    // collection (which GameSessionService.placeBet needs to stay lazily unloaded until
    // after insertBet runs, so the broadcast afterward sees the freshly accepted bet).
    @Query("select bet.player.id from Bet bet where bet.round.id = :roundId")
    Set<Long> findPlayerIdsByRoundId(@Param("roundId") Long roundId);

    // The concurrency-safe core of accepting a bet: a plain INSERT relying on the bets
    // table's own unique constraints (round_id, position) and (round_id, player_id) to
    // reject a genuine conflict as an integrity violation, rather than checking-then-
    // inserting in application code, which would have the same race condition a single
    // atomic statement is specifically built to avoid. GameSessionService.placeBet
    // catches the resulting DataIntegrityViolationException and treats it as a rejected
    // bet, the same way the old tryAcceptBet treated a zero-row UPDATE.
    @Modifying
    @Query(value = "INSERT INTO bets (round_id, player_id, position, placed_at) "
            + "VALUES (:roundId, :playerId, :position, :placedAt)", nativeQuery = true)
    void insertBet(@Param("roundId") Long roundId, @Param("playerId") Long playerId,
                   @Param("position") int position, @Param("placedAt") Instant placedAt);
}
