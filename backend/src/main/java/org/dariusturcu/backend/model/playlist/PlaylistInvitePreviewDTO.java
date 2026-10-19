package org.dariusturcu.backend.model.playlist;

import java.util.List;

public record PlaylistInvitePreviewDTO(
        String name,
        String color,
        int songCount,
        List<PlaylistMemberDTO> members) {
}
