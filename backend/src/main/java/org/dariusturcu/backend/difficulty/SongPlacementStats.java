package org.dariusturcu.backend.difficulty;

// Aggregate outcome of every scored round played on one song: how many rounds reached a
// resolved placement, and how many of those placements were correct. The correct-placement
// fraction is the play-derived difficulty signal, an easier song is placed correctly more
// often. Populated by RoundRepository's grouped aggregate query over scored rounds.
public record SongPlacementStats(
        Long songId,
        long scoredRoundCount,
        long correctPlacementCount
) {
    public double correctPlacementFraction() {
        if (scoredRoundCount == 0) {
            return 0.0;
        }
        return (double) correctPlacementCount / (double) scoredRoundCount;
    }
}
