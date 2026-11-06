package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.group.ChatMessage;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.GroupStatus;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.mapper.UserMapper;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.song.CreateSongRequest;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.user.ChatMessageExportDTO;
import org.dariusturcu.backend.model.user.GroupMembershipExportDTO;
import org.dariusturcu.backend.model.user.PersonalDataExportDTO;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.ChatMessageRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.PlaylistBanRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SavedPlaylistRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = PersonalDataExportIntegrationTest.JpaTestConfig.class)
class PersonalDataExportIntegrationTest {

    private static final int IMPORT_QUOTA_RESOLUTION_COUNT = 7;
    private static final int DEFAULT_RELEASE_YEAR = 2000;
    private static final String YOUTUBE_VIDEO_ID = "dQw4w9WgXcQ";
    private static final String HEX_COLOR = "abcdef";

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    static class JpaTestConfig {
        @Bean
        SongMapper songMapper() {
            return new SongMapper();
        }

        @Bean
        PlaylistMapper playlistMapper(SongMapper songMapper) {
            return new PlaylistMapper(songMapper);
        }

        @Bean
        UserMapper userMapper() {
            return new UserMapper();
        }

        @Bean
        PlaylistAccessService playlistAccessService(PlaylistMembershipRepository playlistMembershipRepository) {
            return new PlaylistAccessService(playlistMembershipRepository);
        }

        @Bean
        CatalogSeedingService catalogSeedingService(PendingImportRepository pendingImportRepository) {
            return new CatalogSeedingService(
                    pendingImportRepository,
                    null,
                    null,
                    null,
                    org.mockito.Mockito.mock(org.dariusturcu.backend.pipeline.PipelineJobService.class));
        }

        @Bean
        SongCatalogService songCatalogService(
                SongRepository songRepository,
                org.dariusturcu.backend.repository.AlternateYoutubeIdRepository alternateYoutubeIdRepository) {
            return new SongCatalogService(songRepository, alternateYoutubeIdRepository);
        }

        @Bean
        PlaylistRemovalService playlistRemovalService(
                PlaylistRepository playlistRepository,
                SavedPlaylistRepository savedPlaylistRepository,
                PlaylistBanRepository playlistBanRepository) {
            return new PlaylistRemovalService(playlistRepository, savedPlaylistRepository, playlistBanRepository);
        }

        @Bean
        PlaylistService playlistService(
                PlaylistRepository playlistRepository,
                SongRepository songRepository,
                PlaylistMapper playlistMapper,
                SongMapper songMapper,
                PlaylistAccessService playlistAccessService,
                PlaylistMembershipRepository playlistMembershipRepository,
                PlaylistBanRepository playlistBanRepository,
                SavedPlaylistRepository savedPlaylistRepository,
                PlaylistRemovalService playlistRemovalService,
                CatalogSeedingService catalogSeedingService,
                SongCatalogService songCatalogService) {
            return new PlaylistService(playlistRepository, songRepository, playlistMapper, songMapper,
                    playlistAccessService, playlistMembershipRepository, playlistBanRepository, savedPlaylistRepository,
                    playlistRemovalService, catalogSeedingService, null, songCatalogService);
        }

        @Bean
        UserService userService(
                UserRepository userRepository,
                PlaylistRepository playlistRepository,
                UserMapper userMapper,
                PlaylistMapper playlistMapper,
                PlaylistMembershipRepository playlistMembershipRepository,
                PlaylistBanRepository playlistBanRepository,
                PlaylistRemovalService playlistRemovalService,
                SongRepository songRepository,
                SavedPlaylistRepository savedPlaylistRepository) {
            return new UserService(
                    userRepository,
                    playlistRepository,
                    userMapper,
                    playlistMapper,
                    playlistMembershipRepository,
                    playlistBanRepository,
                    playlistRemovalService,
                    songRepository,
                    savedPlaylistRepository,
                    org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class));
        }

        @Bean
        PersonalDataExportService personalDataExportService(
                UserRepository userRepository,
                PlaylistMembershipRepository playlistMembershipRepository,
                SongRepository songRepository,
                SongMapper songMapper,
                MemberRepository memberRepository,
                ChatMessageRepository chatMessageRepository,
                org.dariusturcu.backend.repository.SongReportRepository songReportRepository,
                org.dariusturcu.backend.repository.SongConfirmationRepository songConfirmationRepository,
                SavedPlaylistRepository savedPlaylistRepository,
                org.dariusturcu.backend.repository.ImportQuotaUsageRepository importQuotaUsageRepository,
                org.dariusturcu.backend.repository.PlaylistImportJobRepository playlistImportJobRepository) {
            return new PersonalDataExportService(
                    userRepository,
                    playlistMembershipRepository,
                    songRepository,
                    songMapper,
                    memberRepository,
                    chatMessageRepository,
                    songReportRepository,
                    songConfirmationRepository,
                    savedPlaylistRepository,
                    importQuotaUsageRepository,
                    playlistImportJobRepository,
                    org.mockito.Mockito.mock(org.dariusturcu.backend.history.GameHistoryService.class),
                    org.mockito.Mockito.mock(org.dariusturcu.backend.research.SongResearchService.class));
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private PlaylistService playlistService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private PersonalDataExportService personalDataExportService;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate core;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setAuthProvider(org.dariusturcu.backend.model.user.AuthProvider.LOCAL);
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private void actAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    private CreateSongRequest anySongRequest(String title) {
        return new CreateSongRequest(
                "Artist",
                title,
                DEFAULT_RELEASE_YEAR,
                YOUTUBE_VIDEO_ID,
                HEX_COLOR,
                null);
    }

    @Test
    void exportReturnsAllPromisedCategoriesIncludingAccountPlaylistsSongsGroupAndChat() {
        User exportingUser = persistUser("exporter-" + UUID.randomUUID());
        byte[] testAvatarBytes = new byte[]{9, 8, 7, 6};
        exportingUser.setAvatarImage(testAvatarBytes);
        exportingUser.setTwoFactorEnabled(true);
        userRepository.save(exportingUser);

        actAs(exportingUser);

        PlaylistDetailDTO ownedPlaylist = userService.createPlaylist();
        SongDTO submittedSong = playlistService.createSong(ownedPlaylist.id(), anySongRequest("Exported Title"));

        Group group = new Group();
        group.setInviteCode(UUID.randomUUID().toString().substring(0, 8));
        group.setJoinCode("5678");
        group.setStatus(GroupStatus.OPEN);
        group.setDjMode(org.dariusturcu.backend.model.group.DjMode.ROTATING);
        group.setWinConditionCardCount(10);
        group.setCreatedAt(Instant.now());
        Group savedGroup = groupRepository.save(group);

        Member member = new Member();
        member.setGroup(savedGroup);
        member.setUser(exportingUser);
        member.setDisplayName(exportingUser.getUsername());
        member.setJoinedAt(Instant.now());
        member.setAdmin(true);
        member.setConnected(true);
        member.setInVoice(false);
        memberRepository.save(member);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setGroup(savedGroup);
        chatMessage.setSender(exportingUser);
        chatMessage.setContent("Exported chat content");
        chatMessage.setCreatedAt(Instant.now());
        chatMessageRepository.save(chatMessage);

        Long reportedSongId = submittedSong.id();
        core.update("INSERT INTO song_reports (reporter_id, song_id, message, status, created_at) VALUES (?, ?, 'Wrong year', 'OPEN', now())",
                exportingUser.getId(), reportedSongId);
        core.update("INSERT INTO song_confirmations (user_id, song_id, created_at) VALUES (?, ?, now())",
                exportingUser.getId(), reportedSongId);
        core.update("INSERT INTO saved_playlists (user_id, playlist_id, saved_at) VALUES (?, ?, now())",
                exportingUser.getId(), ownedPlaylist.id());
        core.update("INSERT INTO import_quota_usage (user_id, usage_date, resolution_count) VALUES (?, current_date, ?)",
                exportingUser.getId(), IMPORT_QUOTA_RESOLUTION_COUNT);
        String importJobId = UUID.randomUUID().toString();
        core.update("INSERT INTO playlist_import_jobs (id, playlist_id, submitted_by_username, status, created_at) VALUES (?, ?, ?, 'DONE', now())",
                importJobId, ownedPlaylist.id(), exportingUser.getUsername());

        PersonalDataExportDTO export = personalDataExportService.exportCurrentUser();

        assertThat(export.id()).isEqualTo(exportingUser.getId());
        assertThat(export.username()).isEqualTo(exportingUser.getUsername());
        assertThat(export.email()).isEqualTo(exportingUser.getEmail());
        assertThat(export.authProvider()).isEqualTo(org.dariusturcu.backend.model.user.AuthProvider.LOCAL.name());
        assertThat(export.avatarImage()).isEqualTo(testAvatarBytes);
        assertThat(export.twoFactorEnabled()).isTrue();
        assertThat(export.createdAt()).isNotNull();

        assertThat(export.playlists()).hasSize(1);
        assertThat(export.playlists().getFirst().playlistId()).isEqualTo(ownedPlaylist.id());
        assertThat(export.playlists().getFirst().owner()).isTrue();

        assertThat(export.submittedSongs()).extracting(SongDTO::id).containsExactly(submittedSong.id());

        GroupMembershipExportDTO activeGroup = export.activeGroup();
        assertThat(activeGroup).isNotNull();
        assertThat(activeGroup.groupId()).isEqualTo(savedGroup.getId());
        assertThat(activeGroup.isAdmin()).isTrue();
        assertThat(activeGroup.displayName()).isEqualTo(exportingUser.getUsername());

        assertThat(export.chatMessages()).hasSize(1);
        ChatMessageExportDTO exportedMessage = export.chatMessages().getFirst();
        assertThat(exportedMessage.groupId()).isEqualTo(savedGroup.getId());
        assertThat(exportedMessage.content()).isEqualTo("Exported chat content");

        assertThat(export.songReports()).hasSize(1);
        assertThat(export.songReports().getFirst().songId()).isEqualTo(reportedSongId);
        assertThat(export.songReports().getFirst().message()).isEqualTo("Wrong year");
        assertThat(export.songConfirmations()).extracting(confirmation -> confirmation.songId())
                .containsExactly(reportedSongId);
        assertThat(export.savedPlaylists()).extracting(saved -> saved.playlistId())
                .containsExactly(ownedPlaylist.id());
        assertThat(export.importQuotaUsage()).extracting(usage -> usage.resolutionCount())
                .containsExactly(IMPORT_QUOTA_RESOLUTION_COUNT);
        assertThat(export.playlistImportJobs()).extracting(job -> job.id()).containsExactly(importJobId);

        assertThat(export.gameHistory()).isNotNull();
        assertThat(export.researchObservations()).isNotNull();
    }
}
