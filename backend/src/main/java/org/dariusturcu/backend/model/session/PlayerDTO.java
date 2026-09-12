package org.dariusturcu.backend.model.session;

import java.util.List;

public record PlayerDTO(
        Long id,
        String displayName,
        String avatarUrl,
        int turnOrder,
        int tokenCount,
        PlayerStatus status,
        boolean isConnected,
        int totalArtistsGuessed,
        int totalTitlesGuessed,
        List<PlayerCardDTO> timeline) {
}
