package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.NotNull;
import org.dariusturcu.backend.model.user.UserSummaryDTO;

import java.util.List;

public record SongDTO(
        @NotNull
        Long id,
        @NotNull
        List<SongArtistDTO> artists,
        @NotNull
        String title,
        @NotNull
        int releaseYear,
        @NotNull
        String youtubeId,
        String gradientColor1,
        String gradientColor2,
        String genre,
        Country country,
        @NotNull
        VerificationStatus verificationStatus,
        String confidence,
        @NotNull
        UserSummaryDTO addedBy
) {
}
