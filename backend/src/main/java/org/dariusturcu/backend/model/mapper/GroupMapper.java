package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.group.MemberDTO;
import org.dariusturcu.backend.security.util.SecurityUtils;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.function.Supplier;

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
        return toDetailDTO(group, SecurityUtils::getCurrentUserId);
    }

    // A null user id marks every per-user flag false. Scheduler-driven paths
    // (session end and abandon run with no authenticated user) use this
    // instead of failing on the missing security context.
    public GroupDetailDTO toDetailDTO(Group group, Long currentUserId) {
        return toDetailDTO(group, () -> currentUserId);
    }

    private GroupDetailDTO toDetailDTO(Group group, Supplier<Long> userIds) {
        return new GroupDetailDTO(
                group.getId(),
                group.getInviteCode(),
                group.getJoinCode(),
                group.getStatus(),
                group.getDjMode(),
                group.getFixedDjMemberId(),
                group.getWinConditionCardCount(),
                group.getPlaylists().stream()
                        .map(playlist -> playlistMapper.toSummaryDTO(playlist, userIds.get()))
                        .toList(),
                group.getMembers().stream()
                        .map(this::toMemberDTO)
                        .toList(),
                group.getExpiresAt()
        );
    }
}
