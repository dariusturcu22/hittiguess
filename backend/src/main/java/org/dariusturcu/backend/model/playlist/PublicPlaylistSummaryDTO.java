package org.dariusturcu.backend.model.playlist;

import jakarta.validation.constraints.NotNull;
import org.dariusturcu.backend.model.user.UserSummaryDTO;

import java.util.List;

public record PublicPlaylistSummaryDTO(
        @NotNull
        Long id,
        @NotNull
        String name,
        @NotNull
        String color,
        @NotNull
        int songCount,
        @NotNull
        UserSummaryDTO owner,
        // See PlaylistSummaryDTO's own field for what this is.
        @NotNull
        List<String> previewYoutubeIds
) {
}
