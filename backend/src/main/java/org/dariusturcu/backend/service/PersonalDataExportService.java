package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.user.ChatMessageExportDTO;
import org.dariusturcu.backend.model.user.GroupMembershipExportDTO;
import org.dariusturcu.backend.model.user.PersonalDataExportDTO;
import org.dariusturcu.backend.model.user.PlaylistMembershipExportDTO;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.ChatMessageRepository;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Builds the GDPR personal-data export: the requesting user's own account fields, every
// playlist they own or are a member of, submitted songs, active group membership, and chat messages.
@Service
@Transactional(readOnly = true)
public class PersonalDataExportService {
    private final UserRepository userRepository;
    private final PlaylistMembershipRepository playlistMembershipRepository;
    private final SongRepository songRepository;
    private final SongMapper songMapper;
    private final MemberRepository memberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final org.dariusturcu.backend.history.GameHistoryService history;
    private final org.dariusturcu.backend.research.SongResearchService research;

    @Autowired
    public PersonalDataExportService(
            UserRepository userRepository,
            PlaylistMembershipRepository playlistMembershipRepository,
            SongRepository songRepository,
            SongMapper songMapper,
            MemberRepository memberRepository,
            ChatMessageRepository chatMessageRepository,
            org.dariusturcu.backend.history.GameHistoryService history,
            org.dariusturcu.backend.research.SongResearchService research) {
        this.userRepository = userRepository;
        this.playlistMembershipRepository = playlistMembershipRepository;
        this.songRepository = songRepository;
        this.songMapper = songMapper;
        this.memberRepository = memberRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.history = history;
        this.research = research;
    }

    public PersonalDataExportService(
            UserRepository userRepository,
            PlaylistMembershipRepository playlistMembershipRepository,
            SongRepository songRepository,
            SongMapper songMapper,
            org.dariusturcu.backend.history.GameHistoryService history,
            org.dariusturcu.backend.research.SongResearchService research) {
        this(userRepository, playlistMembershipRepository, songRepository, songMapper, null, null, history, research);
    }

    public PersonalDataExportDTO exportCurrentUser() {
        User contextUser = SecurityUtils.getCurrentUser();
        User user = userRepository.findById(contextUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.USER, contextUser.getId()));

        List<PlaylistMembershipExportDTO> playlists = playlistMembershipRepository.findByUserId(user.getId()).stream()
                .map(membership -> toMembershipExportDTO(membership, user))
                .toList();

        List<SongDTO> submittedSongs = songRepository.findByAddedById(user.getId()).stream()
                .map(songMapper::toDTO)
                .toList();

        GroupMembershipExportDTO activeGroup = memberRepository != null
                ? memberRepository.findByUser(user)
                    .map(membership -> new GroupMembershipExportDTO(
                            membership.getGroup().getId(),
                            membership.isAdmin(),
                            membership.getDisplayName(),
                            membership.getAvatarUrl(),
                            membership.getJoinedAt()
                    ))
                    .orElse(null)
                : null;

        List<ChatMessageExportDTO> chatMessages = chatMessageRepository != null
                ? chatMessageRepository.findBySenderIdOrderByCreatedAtAsc(user.getId()).stream()
                    .map(message -> new ChatMessageExportDTO(
                            message.getId(),
                            message.getGroup().getId(),
                            message.getContent(),
                            message.getCreatedAt()
                    ))
                    .toList()
                : List.of();

        return new PersonalDataExportDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getImageUrl(),
                user.getAvatarImage(),
                user.isTwoFactorEnabled(),
                user.getAuthProvider() != null ? user.getAuthProvider().name() : null,
                user.getAuthProviderId(),
                user.getCreatedAt(),
                playlists,
                submittedSongs,
                activeGroup,
                chatMessages,
                history.export(user.getId()),
                research.export(user.getId())
        );
    }

    private PlaylistMembershipExportDTO toMembershipExportDTO(PlaylistMembership membership, User user) {
        return new PlaylistMembershipExportDTO(
                membership.getPlaylist().getId(),
                membership.getPlaylist().getName(),
                membership.getPlaylist().isOwnedBy(user),
                membership.isCanRead(),
                membership.isCanWrite(),
                membership.isCanDelete(),
                membership.getDisplayName(),
                membership.getAvatarUrl(),
                membership.getJoinedAt()
        );
    }
}
