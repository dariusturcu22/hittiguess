package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.group.CreateGroupRequest;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.GroupStatus;
import org.dariusturcu.backend.model.group.JoinGroupRequest;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.group.UpdateGroupSettingsRequest;
import org.dariusturcu.backend.model.mapper.GroupMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.websocket.GroupBroadcastEvent;
import org.dariusturcu.backend.websocket.GroupEventType;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupService {

    private static final Duration PRE_SESSION_WINDOW = Duration.ofMinutes(30);
    private static final Duration BETWEEN_SESSION_WINDOW = Duration.ofMinutes(30);

    private static final String JOIN_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int JOIN_CODE_LENGTH = 4;
    private static final int MAX_JOIN_CODE_GENERATION_ATTEMPTS = 10;

    private static final DjMode DEFAULT_DJ_MODE = DjMode.FIXED;
    private static final int MIN_WIN_CONDITION_CARD_COUNT = 5;
    private static final int SMALL_GROUP_MAX_SIZE = 3;
    private static final int MAX_WIN_CONDITION_CARD_COUNT_SMALL_GROUP = 20;
    private static final int MAX_WIN_CONDITION_CARD_COUNT_LARGE_GROUP = 15;

    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final PlaylistRepository playlistRepository;
    private final GroupMapper groupMapper;
    private final ApplicationEventPublisher eventPublisher;

    private final SecureRandom secureRandom = new SecureRandom();

    public GroupDetailDTO createGroup(CreateGroupRequest request) {
        User creator = SecurityUtils.getCurrentUser();
        requireNoActiveMembership(creator);

        Instant now = Instant.now();
        Group group = new Group();
        group.setInviteCode(UUID.randomUUID().toString());
        group.setJoinCode(generateJoinCode());
        group.setStatus(GroupStatus.OPEN);
        group.setDjMode(DEFAULT_DJ_MODE);
        group.setWinConditionCardCount(MIN_WIN_CONDITION_CARD_COUNT);
        group.setCreatedAt(now);
        group.setExpiresAt(now.plus(PRE_SESSION_WINDOW));

        Member adminMember = newMember(creator, request.displayName(), request.avatarUrl(), true, now);
        group.addMember(adminMember);

        Group savedGroup = groupRepository.save(group);
        return groupMapper.toDetailDTO(savedGroup);
    }

    public GroupDetailDTO joinGroup(JoinGroupRequest request) {
        User user = SecurityUtils.getCurrentUser();
        requireNoActiveMembership(user);

        boolean hasInviteCode = request.inviteCode() != null && !request.inviteCode().isBlank();
        boolean hasJoinCode = request.joinCode() != null && !request.joinCode().isBlank();
        if (hasInviteCode == hasJoinCode) {
            throw new IllegalArgumentException("Provide exactly one of inviteCode or joinCode");
        }

        Group group = hasInviteCode
                ? groupRepository.findByInviteCode(request.inviteCode())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Invite code {" + request.inviteCode() + "} not found"))
                : groupRepository.findByJoinCode(request.joinCode().toUpperCase())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Join code {" + request.joinCode() + "} not found"));

        if (group.getStatus() != GroupStatus.OPEN) {
            throw new ConflictException("This group has already started a game session");
        }
        if (group.isFull()) {
            throw new ConflictException("This group is full");
        }

        Member member = newMember(user, request.displayName(), request.avatarUrl(), false, Instant.now());
        group.addMember(member);

        Group savedGroup = groupRepository.save(group);
        GroupDetailDTO result = groupMapper.toDetailDTO(savedGroup);
        eventPublisher.publishEvent(new GroupBroadcastEvent(GroupEventType.MEMBER_JOINED, result));
        return result;
    }

    @Transactional(readOnly = true)
    public GroupDetailDTO getGroup(Long groupId) {
        Group group = findGroup(groupId);
        requireMembership(group, SecurityUtils.getCurrentUser());
        return groupMapper.toDetailDTO(group);
    }

    @Transactional(readOnly = true)
    public Optional<GroupDetailDTO> getActiveMembership() {
        User user = SecurityUtils.getCurrentUser();
        return memberRepository.findByUser(user)
                .map(Member::getGroup)
                .map(groupMapper::toDetailDTO);
    }

    // The single point settings changes flow through: persists the change and publishes
    // a SETTINGS_CHANGED event, which GroupBroadcastListener forwards to the group's
    // settings topic.
    public GroupDetailDTO updateGroupSettings(Long groupId, UpdateGroupSettingsRequest request) {
        Group group = findGroup(groupId);
        requireAdmin(group, SecurityUtils.getCurrentUser());

        if (request.playlistIds() != null) {
            Set<Playlist> playlists = request.playlistIds().stream()
                    .map(this::findAccessiblePlaylist)
                    .collect(Collectors.toSet());
            group.setPlaylists(playlists);
        }
        if (request.djMode() != null) {
            group.setDjMode(request.djMode());
        }
        if (request.winConditionCardCount() != null) {
            validateWinConditionCardCount(request.winConditionCardCount(), group.getMembers().size());
            group.setWinConditionCardCount(request.winConditionCardCount());
        }

        Group savedGroup = groupRepository.save(group);
        GroupDetailDTO result = groupMapper.toDetailDTO(savedGroup);
        eventPublisher.publishEvent(new GroupBroadcastEvent(GroupEventType.SETTINGS_CHANGED, result));
        return result;
    }

    // Story 10 owns the actual game session model; until it exists, this only flips
    // the group-side state it will hook into: locking the group to new members and
    // clearing the pre-session timer since a session is now in progress.
    public GroupDetailDTO startGameSession(Long groupId) {
        Group group = findGroup(groupId);
        requireAdmin(group, SecurityUtils.getCurrentUser());

        group.setStatus(GroupStatus.LOCKED);
        group.setExpiresAt(null);

        Group savedGroup = groupRepository.save(group);
        GroupDetailDTO result = groupMapper.toDetailDTO(savedGroup);
        eventPublisher.publishEvent(new GroupBroadcastEvent(GroupEventType.GAME_SESSION_STARTED, result));
        return result;
    }

    // Called by GameSessionService once a session ends or is abandoned: unlocks the group
    // for new members and restarts the between-session timer the sweep enforces, handing
    // control back to the group the same way it worked before a session ever started.
    public GroupDetailDTO recordGameSessionEnded(Long groupId) {
        Group group = findGroup(groupId);
        group.setStatus(GroupStatus.OPEN);
        group.setExpiresAt(Instant.now().plus(BETWEEN_SESSION_WINDOW));

        Group savedGroup = groupRepository.save(group);
        return groupMapper.toDetailDTO(savedGroup);
    }

    public void leaveGroup(Long groupId) {
        Group group = findGroup(groupId);
        Member member = requireMembership(group, SecurityUtils.getCurrentUser());
        boolean wasAdmin = member.isAdmin();

        group.removeMember(member);

        if (!wasAdmin) {
            Group savedGroup = groupRepository.save(group);
            eventPublisher.publishEvent(new GroupBroadcastEvent(
                    GroupEventType.MEMBER_LEFT, groupMapper.toDetailDTO(savedGroup)));
            return;
        }

        Optional<Member> nextAdmin = group.getMembers().stream()
                .min(Comparator.comparing(Member::getJoinedAt));

        if (nextAdmin.isPresent()) {
            nextAdmin.get().setAdmin(true);
            Group savedGroup = groupRepository.save(group);
            GroupDetailDTO result = groupMapper.toDetailDTO(savedGroup);
            eventPublisher.publishEvent(new GroupBroadcastEvent(GroupEventType.MEMBER_LEFT, result));
            eventPublisher.publishEvent(new GroupBroadcastEvent(GroupEventType.ADMIN_CHANGED, result));
        } else {
            groupRepository.delete(group);
        }
    }

    public void disconnect(Long groupId) {
        Group group = findGroup(groupId);
        Member member = requireMembership(group, SecurityUtils.getCurrentUser());
        setConnectedAndBroadcast(group, member, false);
    }

    public void reconnect(Long groupId) {
        Group group = findGroup(groupId);
        Member member = requireMembership(group, SecurityUtils.getCurrentUser());
        setConnectedAndBroadcast(group, member, true);
    }

    // The WebSocket disconnect listener's entry point: it only has the user id from the
    // socket session's authenticated principal, not a full request-scoped SecurityContext,
    // so it can't go through disconnect() above. Same effect, resolved by user id instead.
    public void disconnectMember(Long groupId, Long userId) {
        Group group = findGroup(groupId);
        Member member = requireMembershipByUserId(group, userId);
        setConnectedAndBroadcast(group, member, false);
    }

    private void setConnectedAndBroadcast(Group group, Member member, boolean connected) {
        member.setConnected(connected);
        memberRepository.save(member);
        GroupDetailDTO result = groupMapper.toDetailDTO(group);
        eventPublisher.publishEvent(new GroupBroadcastEvent(GroupEventType.MEMBER_CONNECTION_CHANGED, result));
    }

    public GroupDetailDTO promoteMember(Long groupId, Long memberId) {
        Group group = findGroup(groupId);
        Member currentAdmin = requireAdmin(group, SecurityUtils.getCurrentUser());

        if (currentAdmin.getId().equals(memberId)) {
            throw new IllegalArgumentException("This member is already the admin");
        }

        Member target = group.getMembers().stream()
                .filter(candidate -> candidate.getId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.MEMBER, memberId));

        currentAdmin.setAdmin(false);
        target.setAdmin(true);

        Group savedGroup = groupRepository.save(group);
        GroupDetailDTO result = groupMapper.toDetailDTO(savedGroup);
        eventPublisher.publishEvent(new GroupBroadcastEvent(GroupEventType.ADMIN_CHANGED, result));
        return result;
    }

    // Only a presence flag: the WebRTC mesh/signaling mechanics that make voice
    // actually work belong to story 12, blocked on this story and story 11 both shipping.
    public void joinVoice(Long groupId) {
        Group group = findGroup(groupId);
        Member member = requireMembership(group, SecurityUtils.getCurrentUser());
        member.setInVoice(true);
        memberRepository.save(member);
    }

    public void leaveVoice(Long groupId) {
        Group group = findGroup(groupId);
        Member member = requireMembership(group, SecurityUtils.getCurrentUser());
        member.setInVoice(false);
        memberRepository.save(member);
    }

    // Invoked by the scheduled sweep, never by a per-group timer: a sweep can be
    // tested by setting expiresAt directly and calling this method, a per-instance
    // Timer or ScheduledExecutorService task can't be without waiting real minutes.
    public void deleteExpiredGroups() {
        List<Group> expiredGroups = groupRepository.findByExpiresAtBefore(Instant.now());
        groupRepository.deleteAll(expiredGroups);
    }

    private Member newMember(User user, String requestedDisplayName, String requestedAvatarUrl,
                              boolean isAdmin, Instant joinedAt) {
        Member member = new Member();
        member.setUser(user);
        member.setDisplayName(resolveDisplayName(requestedDisplayName, user));
        member.setAvatarUrl(resolveAvatarUrl(requestedAvatarUrl, user));
        member.setAdmin(isAdmin);
        member.setConnected(true);
        member.setInVoice(false);
        member.setJoinedAt(joinedAt);
        return member;
    }

    private void requireNoActiveMembership(User user) {
        if (memberRepository.existsByUser(user)) {
            throw new ConflictException("You are already a member of a group");
        }
    }

    private Group findGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.GROUP, groupId));
    }

    private Member requireMembership(Group group, User user) {
        return requireMembershipByUserId(group, user.getId());
    }

    private Member requireMembershipByUserId(Group group, Long userId) {
        return group.getMembers().stream()
                .filter(member -> member.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this group"));
    }

    private Member requireAdmin(Group group, User user) {
        Member member = requireMembership(group, user);
        if (!member.isAdmin()) {
            throw new AccessDeniedException("Only the group admin can do this");
        }
        return member;
    }

    private Playlist findAccessiblePlaylist(Long playlistId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.PLAYLIST, playlistId));

        User admin = SecurityUtils.getCurrentUser();
        boolean hasAccess = playlist.getUsers().stream()
                .anyMatch(playlistMember -> playlistMember.getId().equals(admin.getId()));
        if (!hasAccess) {
            throw new AccessDeniedException("You are not a member of playlist {id=" + playlistId + "}");
        }
        return playlist;
    }

    private void validateWinConditionCardCount(int requestedCount, int memberCount) {
        int maxAllowed = memberCount <= SMALL_GROUP_MAX_SIZE
                ? MAX_WIN_CONDITION_CARD_COUNT_SMALL_GROUP
                : MAX_WIN_CONDITION_CARD_COUNT_LARGE_GROUP;

        if (requestedCount < MIN_WIN_CONDITION_CARD_COUNT || requestedCount > maxAllowed) {
            throw new IllegalArgumentException(
                    "Win-condition card count must be between " + MIN_WIN_CONDITION_CARD_COUNT
                            + " and " + maxAllowed + " for a group of this size");
        }
    }

    private String resolveDisplayName(String requested, User user) {
        return (requested != null && !requested.isBlank()) ? requested : user.getUsername();
    }

    private String resolveAvatarUrl(String requested, User user) {
        return (requested != null && !requested.isBlank()) ? requested : user.getImageUrl();
    }

    private String generateJoinCode() {
        for (int attempt = 0; attempt < MAX_JOIN_CODE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = randomJoinCode();
            if (!groupRepository.existsByJoinCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique join code");
    }

    private String randomJoinCode() {
        StringBuilder code = new StringBuilder(JOIN_CODE_LENGTH);
        for (int position = 0; position < JOIN_CODE_LENGTH; position++) {
            int letterIndex = secureRandom.nextInt(JOIN_CODE_ALPHABET.length());
            code.append(JOIN_CODE_ALPHABET.charAt(letterIndex));
        }
        return code.toString();
    }
}
