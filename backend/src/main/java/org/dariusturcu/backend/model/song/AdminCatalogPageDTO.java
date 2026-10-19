package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AdminCatalogPageDTO(
        @NotNull List<AdminCatalogSongDTO> items,
        @NotNull Integer page,
        @NotNull Integer size,
        @NotNull Long totalElements,
        @NotNull Integer totalPages,
        @NotNull AdminCatalogCountsDTO counts) {
}
