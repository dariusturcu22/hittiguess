package org.dariusturcu.backend.model.song;

// One verified catalog fact for public reuse: the song's credited artists as a
// single display string, its title, and its locked release year. Carries no
// YouTube-sourced fields by construction.
public record GroundTruthSongDTO(
        String artist,
        String title,
        Integer releaseYear
) {
}
