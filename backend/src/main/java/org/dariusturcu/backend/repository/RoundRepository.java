package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.difficulty.SongPlacementStats;
import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, Long> {

    Optional<Round> findTopBySessionOrderByRoundNumberDesc(GameSession session);

    List<Round> findBySessionOrderByRoundNumberAsc(GameSession session);

    // Per-song aggregate placement outcome across every scored round: the count of scored
    // rounds and, of those, the count whose placement was correct. Only rounds that reached
    // a resolved placement are counted (placedPosition and placementCorrect are null until
    // the active player locks in), so a song's correct-placement fraction reflects real
    // outcomes rather than in-flight rounds. Restricting to a caller-supplied set of song ids
    // keeps the aggregate scoped to the candidate catalog being scored rather than the whole
    // rounds table.
    @Query("""
            select new org.dariusturcu.backend.difficulty.SongPlacementStats(
                round.song.id,
                count(round),
                sum(case when round.placementCorrect = true then 1L else 0L end)
            )
            from Round round
            where round.status = :scoredStatus
              and round.placementCorrect is not null
              and round.song.id in :songIds
            group by round.song.id
            """)
    List<SongPlacementStats> aggregatePlacementStatsBySong(
            @Param("scoredStatus") RoundStatus scoredStatus,
            @Param("songIds") Collection<Long> songIds);
}
