package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.GroupStatus;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.group.MemberDTO;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A member's per-group display name and avatar are chosen independently of their
 * account profile; nothing about the account (username, email, account avatar)
 * should be reachable through a group-scoped DTO.
 */
class GroupMapperTest {

    private final GroupMapper groupMapper = new GroupMapper(new PlaylistMapper(null, null));

    @Test
    void memberDtoCarriesThePerGroupIdentityInsteadOfTheAccountProfile() {
        User user = new User();
        user.setId(1L);
        user.setUsername("real-account-username");
        user.setEmail("real-account-email@example.com");
        user.setImageUrl("real-account-avatar.png");
        user.setRole(Role.USER);

        Group group = new Group();
        group.setId(5L);
        group.setInviteCode("invite-code");
        group.setJoinCode("WXYZ");
        group.setStatus(GroupStatus.OPEN);
        group.setDjMode(DjMode.FIXED);
        group.setWinConditionCardCount(5);
        group.setCreatedAt(Instant.now());
        group.setExpiresAt(Instant.now());

        Member member = new Member();
        member.setId(2L);
        member.setUser(user);
        member.setDisplayName("Party Name");
        member.setAvatarUrl("party-avatar.png");
        member.setAdmin(true);
        member.setConnected(true);
        member.setJoinedAt(Instant.now());
        group.addMember(member);

        MemberDTO memberDTO = groupMapper.toMemberDTO(member);

        assertThat(memberDTO.displayName()).isEqualTo("Party Name");
        assertThat(memberDTO.avatarUrl()).isEqualTo("party-avatar.png");
        assertThat(memberDTO.displayName()).isNotEqualTo(user.getUsername());
        assertThat(memberDTO.avatarUrl()).isNotEqualTo(user.getImageUrl());

        // The DTO's own shape has no accessor for account fields at all, only for
        // the per-group identity: a structural guarantee, not just a value check.
        assertThat(Arrays.stream(MemberDTO.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactlyInAnyOrder(
                        "id", "displayName", "avatarUrl", "isAdmin", "isConnected", "isInVoice", "joinedAt");

        GroupDetailDTO groupDetailDTO = groupMapper.toDetailDTO(group);
        assertThat(groupDetailDTO.members()).containsExactly(memberDTO);
    }
}
