package org.dariusturcu.backend.model.song;

import java.util.List;

// One page of catalog recommendations: the newest verified songs first, with
// whether another page exists so the client knows to offer fetching more.
public record RecommendedSongsDTO(
        List<SongDTO> songs,
        boolean hasMore
) {
}
