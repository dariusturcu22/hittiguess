package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.NotNull;
import org.dariusturcu.backend.model.user.UserSummaryDTO;

import java.util.List;
import java.util.Set;

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
        Set<SongTag> tags,
        Country country,
        @NotNull
        VerificationStatus verificationStatus,
        String confidence,
        @NotNull
        UserSummaryDTO addedBy
) {
}
