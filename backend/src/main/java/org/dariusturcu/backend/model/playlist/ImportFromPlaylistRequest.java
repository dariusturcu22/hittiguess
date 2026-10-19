package org.dariusturcu.backend.model.playlist;

import jakarta.validation.constraints.NotNull;

public record ImportFromPlaylistRequest(
        @NotNull
        Long sourcePlaylistId) {
}
