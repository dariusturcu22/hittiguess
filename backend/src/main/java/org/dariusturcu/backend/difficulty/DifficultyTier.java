package org.dariusturcu.backend.difficulty;

// The three top-level difficulty tiers a Difficulty-Based session is generated for. Easy
// weights selection toward more widely-recognized, easier-to-place songs; hard applies no
// such weighting and can draw the full obscure catalog; medium sits between the two.
public enum DifficultyTier {
    EASY,
    MEDIUM,
    HARD
}
