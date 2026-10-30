package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.model.group.AudioCheckStatus;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.GroupStatus;
import org.dariusturcu.backend.model.group.JoinGroupRequest;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.group.UpdateGroupSettingsRequest;
import org.dariusturcu.backend.model.mapper.GroupMapper;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.GameSessionRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceAudioCheckTest {

    private static final long GROUP_ID = 10L;
    private static final long ADMIN_USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long JOINING_USER_ID = 3L;
    private static final long MEMBER_ID_OFFSET = 100L;

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private GameSessionRepository gameSessionRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PlaylistAccessService playlistAccessService;

    private GroupService groupService;
    private User adminUser;
    private User otherUser;
    private Group group;
    private Member adminMember;
    private Member otherMember;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(groupRepository, memberRepository, gameSessionRepository, playlistRepository,
                new GroupMapper(new PlaylistMapper(null)), eventPublisher, playlistAccessService);
        adminUser = user(ADMIN_USER_ID);
        otherUser = user(OTHER_USER_ID);
        group = new Group();
        group.setId(GROUP_ID);
        group.setInviteCode("invite-code");
        group.setJoinCode("ABCD");
        group.setStatus(GroupStatus.OPEN);
        group.setDjMode(DjMode.ROTATING);
        group.setWinConditionCardCount(5);
        Instant joinedAt = Instant.now();
        adminMember = member(adminUser, true, joinedAt);
        otherMember = member(otherUser, false, joinedAt.plusSeconds(1));
        lenient().when(groupRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
        lenient().when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        lenient().when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAs(adminUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setRole(Role.USER);
        return user;
    }

    private Member member(User user, boolean isAdmin, Instant joinedAt) {
        Member member = new Member();
        member.setId(user.getId() + MEMBER_ID_OFFSET);
        member.setUser(user);
        member.setDisplayName(user.getUsername());
        member.setAdmin(isAdmin);
        member.setConnected(true);
        member.setJoinedAt(joinedAt);
        group.addMember(member);
        return member;
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    private List<GroupEventType> publishedGroupEvents() {
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(events.capture());
        return events.getAllValues().stream()
                .filter(GroupBroadcastEvent.class::isInstance)
                .map(event -> ((GroupBroadcastEvent) event).type())
                .toList();
    }

    @Test
    void startGameOpensTheAudioCheckAndResetsEveryShare() {
        adminMember.setAudioCheckStatus(AudioCheckStatus.OK);

        GroupDetailDTO result = groupService.beginAudioCheck(GROUP_ID);

        assertThat(result.status()).isEqualTo(GroupStatus.AUDIO_CHECK);
        assertThat(result.audioCheck()).isNotNull();
        assertThat(result.audioCheck().requiredMemberIds()).containsExactly(adminMember.getId(), otherMember.getId());
        assertThat(result.expiresAt()).isAfter(Instant.now());
        assertThat(adminMember.getAudioCheckStatus()).isEqualTo(AudioCheckStatus.NOT_SHARED);
        assertThat(publishedGroupEvents()).containsExactly(GroupEventType.AUDIO_CHECK_STARTED);
    }

    @Test
    void onlyTheAdminCanOpenTheAudioCheck() {
        authenticateAs(otherUser);

        assertThatThrownBy(() -> groupService.beginAudioCheck(GROUP_ID)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aReportedShareIsStoredAndBroadcast() {
        group.setStatus(GroupStatus.AUDIO_CHECK);
        authenticateAs(otherUser);

        GroupDetailDTO result = groupService.reportAudioCheckStatus(GROUP_ID, AudioCheckStatus.TESTING);

        assertThat(otherMember.getAudioCheckStatus()).isEqualTo(AudioCheckStatus.TESTING);
        assertThat(result.members()).anySatisfy(member -> {
            assertThat(member.id()).isEqualTo(otherMember.getId());
            assertThat(member.audioCheckStatus()).isEqualTo(AudioCheckStatus.TESTING);
        });
        assertThat(publishedGroupEvents()).containsExactly(GroupEventType.AUDIO_CHECK_UPDATED);
    }

    @Test
    void aBrowserCannotReportThatItCannotShareThroughTheStatus() {
        group.setStatus(GroupStatus.AUDIO_CHECK);

        assertThatThrownBy(() -> groupService.reportAudioCheckStatus(GROUP_ID, AudioCheckStatus.CANNOT_SHARE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aShareIsRefusedOutsideTheAudioCheck() {
        assertThatThrownBy(() -> groupService.reportAudioCheckStatus(GROUP_ID, AudioCheckStatus.OK))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void theGameCannotStartUntilEveryRotatingPlayerPassed() {
        group.setStatus(GroupStatus.AUDIO_CHECK);
        adminMember.setAudioCheckStatus(AudioCheckStatus.OK);
        otherMember.setAudioCheckStatus(AudioCheckStatus.TESTING);

        assertThatThrownBy(() -> groupService.startGameAfterAudioCheck(GROUP_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(otherMember.getDisplayName());
        assertThat(group.getStatus()).isEqualTo(GroupStatus.AUDIO_CHECK);
    }

    @Test
    void aPlayerWhoCannotShareBlocksARotatingStart() {
        group.setStatus(GroupStatus.AUDIO_CHECK);
        adminMember.setAudioCheckStatus(AudioCheckStatus.OK);
        otherMember.setAudioCheckStatus(AudioCheckStatus.OK);
        otherMember.setCanShareAudio(false);

        assertThatThrownBy(() -> groupService.startGameAfterAudioCheck(GROUP_ID)).isInstanceOf(ConflictException.class);
    }

    @Test
    void theGameStartsOnceEveryRequiredShareHasPassed() {
        group.setStatus(GroupStatus.AUDIO_CHECK);
        adminMember.setAudioCheckStatus(AudioCheckStatus.OK);
        otherMember.setAudioCheckStatus(AudioCheckStatus.OK);

        GroupDetailDTO result = groupService.startGameAfterAudioCheck(GROUP_ID);

        assertThat(result.status()).isEqualTo(GroupStatus.LOCKED);
        assertThat(publishedGroupEvents()).containsExactly(GroupEventType.GAME_SESSION_STARTED);
    }

    @Test
    void aFixedGameStartsWithOnlyTheDjPassed() {
        group.setStatus(GroupStatus.AUDIO_CHECK);
        group.setDjMode(DjMode.FIXED);
        group.setFixedDjMemberId(otherMember.getId());
        otherMember.setAudioCheckStatus(AudioCheckStatus.OK);
        adminMember.setCanShareAudio(false);

        GroupDetailDTO result = groupService.startGameAfterAudioCheck(GROUP_ID);

        assertThat(result.status()).isEqualTo(GroupStatus.LOCKED);
    }

    @Test
    void theHostCanSwitchToAFixedDjWhoseSharePassed() {
        group.setStatus(GroupStatus.AUDIO_CHECK);
        otherMember.setAudioCheckStatus(AudioCheckStatus.OK);
        adminMember.setCanShareAudio(false);

        GroupDetailDTO result = groupService.switchToFixedDj(GROUP_ID, otherMember.getId());

        assertThat(result.djMode()).isEqualTo(DjMode.FIXED);
        assertThat(result.fixedDjMemberId()).isEqualTo(otherMember.getId());
        assertThat(result.audioCheck().canStart()).isTrue();
    }

    @Test
    void aFixedDjMustHaveAPassedShare() {
        group.setStatus(GroupStatus.AUDIO_CHECK);
        otherMember.setAudioCheckStatus(AudioCheckStatus.TESTING);

        assertThatThrownBy(() -> groupService.switchToFixedDj(GROUP_ID, otherMember.getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(group.getDjMode()).isEqualTo(DjMode.ROTATING);
    }

    @Test
    void cancellingReturnsTheGroupToItsLobby() {
        group.setStatus(GroupStatus.AUDIO_CHECK);
        otherMember.setAudioCheckStatus(AudioCheckStatus.OK);

        GroupDetailDTO result = groupService.cancelAudioCheck(GROUP_ID);

        assertThat(result.status()).isEqualTo(GroupStatus.OPEN);
        assertThat(result.audioCheck()).isNull();
        assertThat(otherMember.getAudioCheckStatus()).isEqualTo(AudioCheckStatus.NOT_SHARED);
        assertThat(publishedGroupEvents()).containsExactly(GroupEventType.AUDIO_CHECK_CANCELLED);
    }

    @Test
    void aMemberCanJoinDuringTheAudioCheckAndBecomesRequired() {
        group.setStatus(GroupStatus.AUDIO_CHECK);
        User joiningUser = user(JOINING_USER_ID);
        authenticateAs(joiningUser);
        when(groupRepository.findByJoinCodeForUpdate("ABCD")).thenReturn(Optional.of(group));
        when(memberRepository.findByUser(joiningUser)).thenReturn(Optional.empty());

        GroupDetailDTO result = groupService.joinGroup(new JoinGroupRequest(null, "ABCD", null, null, false));

        assertThat(result.members()).hasSize(3);
        assertThat(result.audioCheck().requiredMemberIds()).hasSize(3);
    }

    @Test
    void settingsStayLockedDuringTheAudioCheck() {
        group.setStatus(GroupStatus.AUDIO_CHECK);

        assertThatThrownBy(() -> groupService.updateGroupSettings(GROUP_ID,
                new UpdateGroupSettingsRequest(null, DjMode.FIXED, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aCapabilityReportDuringTheCheckIsBroadcast() {
        group.setStatus(GroupStatus.AUDIO_CHECK);
        authenticateAs(otherUser);

        groupService.reportAudioCapability(GROUP_ID, false);

        assertThat(publishedGroupEvents()).containsExactly(GroupEventType.AUDIO_CHECK_UPDATED);
    }
}
