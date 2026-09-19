package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.group.MemberDTO;
import org.dariusturcu.backend.security.util.SecurityUtils;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GroupMapper {
    private final PlaylistMapper playlistMapper;

    public MemberDTO toMemberDTO(Member member) {
        return new MemberDTO(
                member.getId(),
                member.getUser().getId(),
                member.getDisplayName(),
                member.getAvatarUrl(),
                member.isAdmin(),
                member.isConnected(),
                member.isInVoice(),
                member.getJoinedAt()
        );
    }

    public GroupDetailDTO toDetailDTO(Group group) {
        return new GroupDetailDTO(
                group.getId(),
                group.getInviteCode(),
                group.getJoinCode(),
                group.getStatus(),
                group.getDjMode(),
                group.getWinConditionCardCount(),
                group.getPlaylists().stream()
                        .map(playlist -> playlistMapper.toSummaryDTO(playlist, SecurityUtils.getCurrentUserId()))
                        .toList(),
                group.getMembers().stream()
                        .map(this::toMemberDTO)
                        .toList(),
                group.getExpiresAt()
        );
    }
}
