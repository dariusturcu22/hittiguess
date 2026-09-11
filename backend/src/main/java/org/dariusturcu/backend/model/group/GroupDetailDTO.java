package org.dariusturcu.backend.model.group;

import org.dariusturcu.backend.model.playlist.PlaylistSummaryDTO;

import java.time.Instant;
import java.util.List;

public record GroupDetailDTO(
        Long id,
        String inviteCode,
        String joinCode,
        GroupStatus status,
        DjMode djMode,
        int winConditionCardCount,
        List<PlaylistSummaryDTO> playlists,
        List<MemberDTO> members,
        Instant expiresAt) {
}
