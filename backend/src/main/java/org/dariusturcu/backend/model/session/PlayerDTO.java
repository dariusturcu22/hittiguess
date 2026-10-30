package org.dariusturcu.backend.model.session;

import org.dariusturcu.backend.model.group.AudioCheckStatus;

import java.util.List;

public record PlayerDTO(
        Long id,
        Long userId,
        String displayName,
        String avatarUrl,
        int turnOrder,
        int tokenCount,
        PlayerStatus status,
        boolean isConnected,
        boolean canDj,
        AudioCheckStatus audioStatus,
        int totalArtistsGuessed,
        int totalTitlesGuessed,
        List<PlayerCardDTO> timeline) {
}
