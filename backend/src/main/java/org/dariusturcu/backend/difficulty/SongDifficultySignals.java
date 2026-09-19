package org.dariusturcu.backend.difficulty;

import java.util.Optional;

// The raw per-song inputs the difficulty scorer combines into one score. Placement stats
// come from real scored rounds and are absent for a song nobody has played yet. The
// Wikidata sitelinks count is the cold-start popularity proxy; it is Optional because the
// column that would persist it does not exist on Song yet. Capturing it end to end through
// the metadata pipeline is deferred to story 23, so today every song reports it empty and
// scoring falls back to the neutral default. See docs/DECISIONS.md.
public record SongDifficultySignals(
        Long songId,
        SongPlacementStats placementStats,
        Optional<Integer> wikidataSitelinksCount
) {
    public boolean hasPlacementHistory() {
        return placementStats != null && placementStats.scoredRoundCount() > 0;
    }
}
