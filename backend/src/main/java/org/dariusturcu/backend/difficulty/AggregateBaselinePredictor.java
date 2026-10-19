package org.dariusturcu.backend.difficulty;

import org.springframework.stereotype.Component;

// The shipped personalized predictor: it returns the song's aggregate baseline difficulty
// unchanged for every player, since no trained collaborative-filtering model exists yet.
// This is the baseline the eventual personalized model must beat before it replaces this
// implementation. See docs/DECISIONS.md.
@Component
public class AggregateBaselinePredictor implements PersonalizedDifficultyPredictor {

    @Override
    public double predictForPlayer(Long playerId, Long songId, double baselineDifficultyScore) {
        return baselineDifficultyScore;
    }
}
