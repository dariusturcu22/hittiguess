package org.dariusturcu.backend.model.song;

import java.util.List;

// The paged ground-truth payload: the triple list plus the page position, an
// explicit envelope rather than Spring's Page JSON, whose sort and pageable
// internals are not part of this public contract.
public record GroundTruthSongsResponse(
        List<GroundTruthSongDTO> songs,
        int page,
        int size,
        long totalElements
) {
}
