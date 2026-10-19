package org.dariusturcu.backend.difficulty;

import org.dariusturcu.backend.model.song.Song;

// One candidate song paired with the group-level difficulty score it earned for the players
// it was scored against, and the tier that score falls in. Returned by the selector so a
// caller can inspect why a song was chosen for a tier.
public record ScoredSong(
        Song song,
        double groupDifficultyScore,
        DifficultyTier tier
) {
}
