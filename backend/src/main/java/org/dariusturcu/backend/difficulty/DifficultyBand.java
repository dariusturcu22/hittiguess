package org.dariusturcu.backend.difficulty;

import org.springframework.stereotype.Component;

// Maps a continuous difficulty score in [0, 1] to the discrete tier a song belongs to, so
// selection for a target tier can filter the scored catalog to the matching band. The two
// cut points are initial heuristics that split the range into three parts; they are named
// constants to be tuned once real catalog and play data exist, not fitted values. See
// docs/DECISIONS.md.
@Component
public class DifficultyBand {

    static final double EASY_BAND_UPPER_BOUND = 0.34;
    static final double MEDIUM_BAND_UPPER_BOUND = 0.67;

    public DifficultyTier tierForScore(double difficultyScore) {
        if (difficultyScore < EASY_BAND_UPPER_BOUND) {
            return DifficultyTier.EASY;
        }
        if (difficultyScore < MEDIUM_BAND_UPPER_BOUND) {
            return DifficultyTier.MEDIUM;
        }
        return DifficultyTier.HARD;
    }
}
