package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    List<Player> findBySessionOrderByTurnOrderAsc(GameSession session);

    // Deducts exactly one token, guarded so a token count can never go negative even
    // under a race; used the moment a bet is accepted (see RoundRepository.tryAcceptBet),
    // never refunded regardless of how the round resolves.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Player player SET player.tokenCount = player.tokenCount - 1 "
            + "WHERE player.id = :playerId AND player.tokenCount > 0")
    int deductToken(@Param("playerId") Long playerId);
}
