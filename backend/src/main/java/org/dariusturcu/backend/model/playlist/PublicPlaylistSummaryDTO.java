package org.dariusturcu.backend.model.playlist;

import jakarta.validation.constraints.NotNull;
import org.dariusturcu.backend.model.user.UserSummaryDTO;

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
        UserSummaryDTO owner
) {
}
