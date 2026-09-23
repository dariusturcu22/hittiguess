package org.dariusturcu.backend.model.group;

import java.util.List;

public record GroupInvitePreviewDTO(
        int memberCount,
        List<GroupInvitePreviewMemberDTO> members) {
}
