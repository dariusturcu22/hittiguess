package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.dariusturcu.backend.validation.NotFutureYear;

public record CreateSongRequest(
        @NotBlank(message = "Artist name is required")
        String artist,

        @NotBlank(message = "Song title is required")
        String title,

        @Min(value = 1000, message = "Release year must be 1000 or later")
        @NotFutureYear
        int releaseYear,

        @NotBlank(message = "YouTube link is required")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{11}$", message = "Must be a valid YouTube video ID")
        String youtubeId,

        @NotBlank(message = "Both gradient colors are required")
        @Pattern(regexp = "^[0-9a-fA-F]{6}$", message = "Must be a 6-character hex color without #")
        String gradientColor1,

        @NotBlank(message = "Both gradient colors are required")
        @Pattern(regexp = "^[0-9a-fA-F]{6}$", message = "Must be a 6-character hex color without #")
        String gradientColor2,

        SongTag songTag,

        Country country
) {
}
