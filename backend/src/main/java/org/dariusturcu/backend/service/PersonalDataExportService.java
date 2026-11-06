package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.user.ChatMessageExportDTO;
import org.dariusturcu.backend.model.user.GroupMembershipExportDTO;
import org.dariusturcu.backend.model.user.ImportQuotaUsageExportDTO;
import org.dariusturcu.backend.model.user.PersonalDataExportDTO;
import org.dariusturcu.backend.model.user.PlaylistImportJobExportDTO;
import org.dariusturcu.backend.model.user.SavedPlaylistExportDTO;
import org.dariusturcu.backend.model.user.SongConfirmationExportDTO;
import org.dariusturcu.backend.model.user.SongReportExportDTO;
import org.dariusturcu.backend.model.user.PlaylistMembershipExportDTO;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.ChatMessageRepository;
import org.dariusturcu.backend.repository.ImportQuotaUsageRepository;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PlaylistImportJobRepository;
import org.dariusturcu.backend.repository.SavedPlaylistRepository;
import org.dariusturcu.backend.repository.SongConfirmationRepository;
import org.dariusturcu.backend.repository.SongReportRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Builds the GDPR personal-data export: the requesting user's own account fields, every
// playlist they own or are a member of, submitted songs, active group membership, chat messages,
// their own song reports and confirmations, saved playlists, import quota usage, and the playlist
// import jobs they started. Pipeline run rows and fingerprints hold no per-user data.
@Service
@Transactional(readOnly = true)
public class PersonalDataExportService {
    private final UserRepository userRepository;
    private final PlaylistMembershipRepository playlistMembershipRepository;
    private final SongRepository songRepository;
    private final SongMapper songMapper;
    private final MemberRepository memberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SongReportRepository songReportRepository;
    private final SongConfirmationRepository songConfirmationRepository;
    private final SavedPlaylistRepository savedPlaylistRepository;
    private final ImportQuotaUsageRepository importQuotaUsageRepository;
    private final PlaylistImportJobRepository playlistImportJobRepository;
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
            SongReportRepository songReportRepository,
            SongConfirmationRepository songConfirmationRepository,
            SavedPlaylistRepository savedPlaylistRepository,
            ImportQuotaUsageRepository importQuotaUsageRepository,
            PlaylistImportJobRepository playlistImportJobRepository,
            org.dariusturcu.backend.history.GameHistoryService history,
            org.dariusturcu.backend.research.SongResearchService research) {
        this.userRepository = userRepository;
        this.playlistMembershipRepository = playlistMembershipRepository;
        this.songRepository = songRepository;
        this.songMapper = songMapper;
        this.memberRepository = memberRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.songReportRepository = songReportRepository;
        this.songConfirmationRepository = songConfirmationRepository;
        this.savedPlaylistRepository = savedPlaylistRepository;
        this.importQuotaUsageRepository = importQuotaUsageRepository;
        this.playlistImportJobRepository = playlistImportJobRepository;
        this.history = history;
        this.research = research;
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

        List<SongReportExportDTO> songReports = songReportRepository.findByReporterId(user.getId()).stream()
                .map(report -> new SongReportExportDTO(
                        report.getId(),
                        report.getSong().getId(),
                        report.getMessage(),
                        report.getSuggestedCorrectYear(),
                        report.getSources(),
                        report.getStatus(),
                        report.getCreatedAt()))
                .toList();

        List<SongConfirmationExportDTO> songConfirmations = songConfirmationRepository.findByUserId(user.getId()).stream()
                .map(confirmation -> new SongConfirmationExportDTO(
                        confirmation.getId(),
                        confirmation.getSong().getId(),
                        confirmation.getCreatedAt()))
                .toList();

        List<SavedPlaylistExportDTO> savedPlaylists = savedPlaylistRepository.findByUserId(user.getId()).stream()
                .map(saved -> new SavedPlaylistExportDTO(
                        saved.getPlaylist().getId(),
                        saved.getPlaylist().getName(),
                        saved.getSavedAt()))
                .toList();

        List<ImportQuotaUsageExportDTO> importQuotaUsage = importQuotaUsageRepository
                .findByUserIdOrderByUsageDateAsc(user.getId()).stream()
                .map(usage -> new ImportQuotaUsageExportDTO(usage.getUsageDate(), usage.getResolutionCount()))
                .toList();

        // Import jobs record their submitter by username, not by user id.
        List<PlaylistImportJobExportDTO> playlistImportJobs = playlistImportJobRepository
                .findBySubmittedByUsernameOrderByCreatedAtAsc(user.getUsername()).stream()
                .map(job -> new PlaylistImportJobExportDTO(
                        job.getId(),
                        job.getPlaylist().getId(),
                        job.getStatus(),
                        job.getCreatedAt(),
                        job.getCompletedAt()))
                .toList();

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
                songReports,
                songConfirmations,
                savedPlaylists,
                importQuotaUsage,
                playlistImportJobs,
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
