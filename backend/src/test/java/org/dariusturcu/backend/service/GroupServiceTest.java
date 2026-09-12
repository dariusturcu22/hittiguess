package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.model.group.CreateGroupRequest;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.GroupStatus;
import org.dariusturcu.backend.model.group.JoinGroupRequest;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.group.MemberDTO;
import org.dariusturcu.backend.model.group.UpdateGroupSettingsRequest;
import org.dariusturcu.backend.model.mapper.GroupMapper;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.websocket.GroupBroadcastEvent;
import org.dariusturcu.backend.websocket.GroupEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private GroupMapper groupMapper;

    private GroupService groupService;

    private User adminUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin-user");
        adminUser.setImageUrl("admin-avatar.png");
        adminUser.setRole(Role.USER);

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("other-user");
        otherUser.setImageUrl("other-avatar.png");
        otherUser.setRole(Role.USER);

        groupMapper = new GroupMapper(new PlaylistMapper(null, null));
        groupService = new GroupService(groupRepository, memberRepository, playlistRepository, groupMapper, eventPublisher);

        lenient().when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authenticateAs(adminUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null)
        );
    }

    private Member memberOf(Group group, User user, boolean isAdmin, Instant joinedAt) {
        Member member = new Member();
        member.setId(user.getId() + 100);
        member.setUser(user);
        member.setDisplayName(user.getUsername());
        member.setAdmin(isAdmin);
        member.setConnected(true);
        member.setJoinedAt(joinedAt);
        group.addMember(member);
        return member;
    }

    private Group groupWithAdmin() {
        Group group = new Group();
        group.setId(10L);
        group.setInviteCode("invite-code");
        group.setJoinCode("ABCD");
        group.setStatus(GroupStatus.OPEN);
        group.setDjMode(DjMode.FIXED);
        group.setWinConditionCardCount(5);
        Instant now = Instant.now();
        group.setCreatedAt(now);
        group.setExpiresAt(now.plus(30, ChronoUnit.MINUTES));
        memberOf(group, adminUser, true, now);
        return group;
    }

    @Test
    void createGroupMakesCreatorAdminAndGeneratesCodes() {
        when(memberRepository.existsByUser(adminUser)).thenReturn(false);
        when(groupRepository.existsByJoinCode(anyString())).thenReturn(false);

        GroupDetailDTO result = groupService.createGroup(new CreateGroupRequest(null, null));

        assertThat(result.members()).hasSize(1);
        assertThat(result.members().getFirst().isAdmin()).isTrue();
        assertThat(result.members().getFirst().displayName()).isEqualTo("admin-user");
        assertThat(result.joinCode()).matches("^[A-Z]{4}$");
        assertThat(result.inviteCode()).isNotBlank();
        assertThat(result.status()).isEqualTo(GroupStatus.OPEN);
    }

    @Test
    void createGroupUsesRequestedDisplayNameAndAvatarWhenGiven() {
        when(memberRepository.existsByUser(adminUser)).thenReturn(false);
        when(groupRepository.existsByJoinCode(anyString())).thenReturn(false);

        GroupDetailDTO result = groupService.createGroup(new CreateGroupRequest("Custom Name", "custom.png"));

        assertThat(result.members().getFirst().displayName()).isEqualTo("Custom Name");
        assertThat(result.members().getFirst().avatarUrl()).isEqualTo("custom.png");
    }

    @Test
    void createGroupRejectsAUserAlreadyInAGroup() {
        when(memberRepository.existsByUser(adminUser)).thenReturn(true);

        assertThatThrownBy(() -> groupService.createGroup(new CreateGroupRequest(null, null)))
                .isInstanceOf(ConflictException.class);

        verify(groupRepository, never()).save(any());
    }

    @Test
    void joinCodeGenerationRetriesOnCollisionUntilAUniqueOneIsFound() {
        when(memberRepository.existsByUser(adminUser)).thenReturn(false);
        when(groupRepository.existsByJoinCode(anyString())).thenReturn(true, true, false);

        groupService.createGroup(new CreateGroupRequest(null, null));

        verify(groupRepository, times(3)).existsByJoinCode(anyString());
    }

    @Test
    void joinGroupRejectsAUserAlreadyInAGroup() {
        when(memberRepository.existsByUser(otherUser)).thenReturn(true);
        authenticateAs(otherUser);

        assertThatThrownBy(() -> groupService.joinGroup(new JoinGroupRequest("some-code", null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void joinGroupRejectsWhenBothCodesAreGiven() {
        when(memberRepository.existsByUser(otherUser)).thenReturn(false);
        authenticateAs(otherUser);

        assertThatThrownBy(() -> groupService.joinGroup(new JoinGroupRequest("invite", "JOIN", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void joinGroupRejectsWhenNeitherCodeIsGiven() {
        when(memberRepository.existsByUser(otherUser)).thenReturn(false);
        authenticateAs(otherUser);

        assertThatThrownBy(() -> groupService.joinGroup(new JoinGroupRequest(null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void joinGroupByJoinCodeRejectsAGroupThatAlreadyStartedASession() {
        Group group = groupWithAdmin();
        group.setStatus(GroupStatus.LOCKED);
        when(memberRepository.existsByUser(otherUser)).thenReturn(false);
        when(groupRepository.findByJoinCode("ABCD")).thenReturn(Optional.of(group));
        authenticateAs(otherUser);

        assertThatThrownBy(() -> groupService.joinGroup(new JoinGroupRequest(null, "ABCD", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void joinGroupByInviteLinkAddsAMemberDefaultingToAccountIdentity() {
        Group group = groupWithAdmin();
        when(memberRepository.existsByUser(otherUser)).thenReturn(false);
        when(groupRepository.findByInviteCode("invite-code")).thenReturn(Optional.of(group));
        authenticateAs(otherUser);

        GroupDetailDTO result = groupService.joinGroup(new JoinGroupRequest("invite-code", null, null, null));

        assertThat(result.members()).hasSize(2);
        assertThat(result.members().get(1).displayName()).isEqualTo("other-user");
        assertThat(result.members().get(1).isAdmin()).isFalse();
    }

    @Test
    void promoteMemberSwapsWhichMemberIsAdmin() {
        Group group = groupWithAdmin();
        Member secondMember = memberOf(group, otherUser, false, Instant.now());
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        GroupDetailDTO result = groupService.promoteMember(10L, secondMember.getId());

        assertThat(result.members().stream().filter(GroupServiceTest::isAdminMember).count()).isEqualTo(1);
        assertThat(secondMember.isAdmin()).isTrue();
        assertThat(group.getMembers().getFirst().isAdmin()).isFalse();
    }

    private static boolean isAdminMember(MemberDTO memberDTO) {
        return memberDTO.isAdmin();
    }

    @Test
    void promoteMemberRejectsARequesterWhoIsNotAdmin() {
        Group group = groupWithAdmin();
        Member secondMember = memberOf(group, otherUser, false, Instant.now());
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        authenticateAs(otherUser);

        assertThatThrownBy(() -> groupService.promoteMember(10L, secondMember.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void promoteMemberRejectsPromotingTheCurrentAdmin() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        Long currentAdminMemberId = group.getMembers().getFirst().getId();

        assertThatThrownBy(() -> groupService.promoteMember(10L, currentAdminMemberId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leaveGroupRemovesMembershipForANonAdmin() {
        Group group = groupWithAdmin();
        Member secondMember = memberOf(group, otherUser, false, Instant.now());
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        authenticateAs(otherUser);

        groupService.leaveGroup(10L);

        assertThat(group.getMembers()).doesNotContain(secondMember);
        verify(groupRepository, never()).delete(any());
    }

    @Test
    void leaveGroupPromotesTheEarliestJoinedRemainingMemberWhenTheAdminLeaves() {
        Group group = groupWithAdmin();
        Instant earlier = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant later = Instant.now().minus(1, ChronoUnit.MINUTES);
        User thirdUser = new User();
        thirdUser.setId(3L);
        thirdUser.setUsername("third-user");
        Member earlierJoiner = memberOf(group, otherUser, false, earlier);
        memberOf(group, thirdUser, false, later);
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        groupService.leaveGroup(10L);

        assertThat(group.getMembers()).hasSize(2);
        assertThat(earlierJoiner.isAdmin()).isTrue();
        verify(groupRepository, never()).delete(any());
    }

    @Test
    void leaveGroupDeletesTheGroupWhenTheAdminLeavesAndNoMembersRemain() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        groupService.leaveGroup(10L);

        verify(groupRepository).delete(group);
        verify(groupRepository, never()).save(any());
    }

    @Test
    void disconnectFlipsTheConnectionFlagWithoutRemovingMembership() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        groupService.disconnect(10L);

        Member adminMember = group.getMembers().getFirst();
        assertThat(adminMember.isConnected()).isFalse();
        assertThat(group.getMembers()).contains(adminMember);
        verify(groupRepository, never()).delete(any());
        verify(groupRepository, never()).save(any());
    }

    @Test
    void updateGroupSettingsRejectsANonAdmin() {
        Group group = groupWithAdmin();
        memberOf(group, otherUser, false, Instant.now());
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        authenticateAs(otherUser);

        assertThatThrownBy(() -> groupService.updateGroupSettings(
                10L, new UpdateGroupSettingsRequest(null, DjMode.ROTATING, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateGroupSettingsRejectsAWinConditionCountBelowTheMinimum() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.updateGroupSettings(
                10L, new UpdateGroupSettingsRequest(null, null, 4)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateGroupSettingsRejectsAWinConditionCountAboveTheSmallGroupMaximum() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.updateGroupSettings(
                10L, new UpdateGroupSettingsRequest(null, null, 21)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateGroupSettingsAcceptsTheMinimumBoundaryForASmallGroup() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        GroupDetailDTO result = groupService.updateGroupSettings(
                10L, new UpdateGroupSettingsRequest(null, null, 5));

        assertThat(result.winConditionCardCount()).isEqualTo(5);
    }

    @Test
    void updateGroupSettingsAcceptsTheMaximumBoundaryForASmallGroup() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        GroupDetailDTO result = groupService.updateGroupSettings(
                10L, new UpdateGroupSettingsRequest(null, null, 20));

        assertThat(result.winConditionCardCount()).isEqualTo(20);
    }

    @Test
    void updateGroupSettingsRejectsAWinConditionCountAboveTheLargeGroupMaximum() {
        Group group = groupWithAdmin();
        memberOf(group, otherUser, false, Instant.now());
        User thirdUser = new User();
        thirdUser.setId(3L);
        User fourthUser = new User();
        fourthUser.setId(4L);
        memberOf(group, thirdUser, false, Instant.now());
        memberOf(group, fourthUser, false, Instant.now());
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.updateGroupSettings(
                10L, new UpdateGroupSettingsRequest(null, null, 16)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateGroupSettingsAcceptsTheMaximumBoundaryForALargeGroup() {
        Group group = groupWithAdmin();
        memberOf(group, otherUser, false, Instant.now());
        User thirdUser = new User();
        thirdUser.setId(3L);
        User fourthUser = new User();
        fourthUser.setId(4L);
        memberOf(group, thirdUser, false, Instant.now());
        memberOf(group, fourthUser, false, Instant.now());
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        GroupDetailDTO result = groupService.updateGroupSettings(
                10L, new UpdateGroupSettingsRequest(null, null, 15));

        assertThat(result.winConditionCardCount()).isEqualTo(15);
    }

    @Test
    void updateGroupSettingsAppliesALargeGroupMaximumOnceFourOrMoreMembersHaveJoined() {
        Group group = groupWithAdmin();
        memberOf(group, otherUser, false, Instant.now());
        User thirdUser = new User();
        thirdUser.setId(3L);
        User fourthUser = new User();
        fourthUser.setId(4L);
        memberOf(group, thirdUser, false, Instant.now());
        memberOf(group, fourthUser, false, Instant.now());
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.updateGroupSettings(
                10L, new UpdateGroupSettingsRequest(null, null, 20)))
                .isInstanceOf(IllegalArgumentException.class);

        GroupDetailDTO result = groupService.updateGroupSettings(
                10L, new UpdateGroupSettingsRequest(null, null, 15));
        assertThat(result.winConditionCardCount()).isEqualTo(15);
    }

    @Test
    void startGameSessionLocksTheGroupAndClearsThePreSessionTimer() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        GroupDetailDTO result = groupService.startGameSession(10L);

        assertThat(result.status()).isEqualTo(GroupStatus.LOCKED);
        assertThat(result.expiresAt()).isNull();
    }

    @Test
    void recordGameSessionEndedRestartsTheBetweenSessionTimer() {
        Group group = groupWithAdmin();
        group.setStatus(GroupStatus.LOCKED);
        group.setExpiresAt(null);
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        GroupDetailDTO result = groupService.recordGameSessionEnded(10L);

        assertThat(result.expiresAt()).isAfter(Instant.now());
        assertThat(result.status()).isEqualTo(GroupStatus.OPEN);
    }

    @Test
    void deleteExpiredGroupsDeletesOnlyGroupsPastTheirExpiry() {
        Group expiredGroup = groupWithAdmin();
        when(groupRepository.findByExpiresAtBefore(any(Instant.class))).thenReturn(List.of(expiredGroup));

        groupService.deleteExpiredGroups();

        ArgumentCaptor<List<Group>> captor = ArgumentCaptor.forClass(List.class);
        verify(groupRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(expiredGroup);
    }

    @Test
    void getGroupRejectsANonMember() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        authenticateAs(otherUser);

        assertThatThrownBy(() -> groupService.getGroup(10L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getGroupThrowsWhenTheGroupDoesNotExist() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.getGroup(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void joinGroupPublishesAMemberJoinedEvent() {
        Group group = groupWithAdmin();
        when(memberRepository.existsByUser(otherUser)).thenReturn(false);
        when(groupRepository.findByInviteCode("invite-code")).thenReturn(Optional.of(group));
        authenticateAs(otherUser);

        groupService.joinGroup(new JoinGroupRequest("invite-code", null, null, null));

        ArgumentCaptor<GroupBroadcastEvent> captor = ArgumentCaptor.forClass(GroupBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(GroupEventType.MEMBER_JOINED);
        assertThat(captor.getValue().group().members()).hasSize(2);
    }

    @Test
    void leaveGroupPublishesAMemberLeftEventForANonAdmin() {
        Group group = groupWithAdmin();
        memberOf(group, otherUser, false, Instant.now());
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        authenticateAs(otherUser);

        groupService.leaveGroup(10L);

        ArgumentCaptor<GroupBroadcastEvent> captor = ArgumentCaptor.forClass(GroupBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(GroupEventType.MEMBER_LEFT);
    }

    @Test
    void leaveGroupPublishesBothMemberLeftAndAdminChangedWhenTheAdminLeaves() {
        Group group = groupWithAdmin();
        memberOf(group, otherUser, false, Instant.now());
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        groupService.leaveGroup(10L);

        ArgumentCaptor<GroupBroadcastEvent> captor = ArgumentCaptor.forClass(GroupBroadcastEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(GroupBroadcastEvent::type)
                .containsExactlyInAnyOrder(GroupEventType.MEMBER_LEFT, GroupEventType.ADMIN_CHANGED);
    }

    @Test
    void leaveGroupPublishesNoEventWhenTheGroupIsDeleted() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        groupService.leaveGroup(10L);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateGroupSettingsPublishesASettingsChangedEvent() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        groupService.updateGroupSettings(10L, new UpdateGroupSettingsRequest(null, DjMode.ROTATING, null));

        ArgumentCaptor<GroupBroadcastEvent> captor = ArgumentCaptor.forClass(GroupBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(GroupEventType.SETTINGS_CHANGED);
    }

    @Test
    void startGameSessionPublishesAGameSessionStartedEvent() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        groupService.startGameSession(10L);

        ArgumentCaptor<GroupBroadcastEvent> captor = ArgumentCaptor.forClass(GroupBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(GroupEventType.GAME_SESSION_STARTED);
    }

    @Test
    void promoteMemberPublishesAnAdminChangedEvent() {
        Group group = groupWithAdmin();
        Member secondMember = memberOf(group, otherUser, false, Instant.now());
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        groupService.promoteMember(10L, secondMember.getId());

        ArgumentCaptor<GroupBroadcastEvent> captor = ArgumentCaptor.forClass(GroupBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(GroupEventType.ADMIN_CHANGED);
    }

    @Test
    void disconnectPublishesAMemberConnectionChangedEvent() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        groupService.disconnect(10L);

        ArgumentCaptor<GroupBroadcastEvent> captor = ArgumentCaptor.forClass(GroupBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(GroupEventType.MEMBER_CONNECTION_CHANGED);
    }

    @Test
    void disconnectMemberFlipsTheConnectionFlagByUserIdWithoutRemovingMembership() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        groupService.disconnectMember(10L, adminUser.getId());

        Member adminMember = group.getMembers().getFirst();
        assertThat(adminMember.isConnected()).isFalse();
        assertThat(group.getMembers()).contains(adminMember);
        verify(groupRepository, never()).delete(any());
        verify(groupRepository, never()).save(any());

        ArgumentCaptor<GroupBroadcastEvent> captor = ArgumentCaptor.forClass(GroupBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(GroupEventType.MEMBER_CONNECTION_CHANGED);
    }

    @Test
    void disconnectMemberRejectsAUserWhoIsNotAMember() {
        Group group = groupWithAdmin();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.disconnectMember(10L, otherUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
