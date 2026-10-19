package org.dariusturcu.backend.difficulty;

// The plug point for the personalized difficulty layer described in story 30: a per-player,
// per-song predicted difficulty learned from interaction patterns across all players and
// songs (collaborative filtering). A real implementation depends on enough accumulated Guess
// data to beat the per-song aggregate baseline, which does not exist until real rounds have
// been played over time, so the shipped implementation returns the aggregate baseline for
// every player. A trained model plugs in here without changing selection or scoring. See
// docs/DECISIONS.md.
public interface PersonalizedDifficultyPredictor {

    // Predicts how hard the given song is for the given player, in the same [0, 1] difficulty
    // range the scorer produces, given the song's baseline difficulty score. A cold-start
    // player or song falls back to that baseline.
    double predictForPlayer(Long playerId, Long songId, double baselineDifficultyScore);
}
