package org.dariusturcu.backend.model.session;

import org.dariusturcu.backend.difficulty.DifficultyTier;

// Asks for a Difficulty-Based song set for the admin's review step: the tier to generate
// for and how many cards the session needs, headroom above the win condition included.
public record GenerateDifficultySetRequest(DifficultyTier tier, int targetCardCount) {
}
