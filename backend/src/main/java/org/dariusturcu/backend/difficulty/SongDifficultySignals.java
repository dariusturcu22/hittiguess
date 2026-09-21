package org.dariusturcu.backend.difficulty;

import java.util.Optional;

// The raw per-song inputs the difficulty scorer combines into one score. Placement stats
// come from real scored rounds and are absent for a song nobody has played yet. The
// Wikidata sitelinks count is the cold-start popularity proxy; it is Optional because a
// null on Song means the count is unknown, and scoring falls back to the neutral default
// for those songs. See docs/DECISIONS.md.
public record SongDifficultySignals(
        Long songId,
        SongPlacementStats placementStats,
        Optional<Integer> wikidataSitelinksCount
) {
    public boolean hasPlacementHistory() {
        return placementStats != null && placementStats.scoredRoundCount() > 0;
    }
}
