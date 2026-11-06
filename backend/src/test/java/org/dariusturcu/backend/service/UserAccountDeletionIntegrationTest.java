package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.TwoFactorBackupCode;
import org.dariusturcu.backend.model.group.ChatMessage;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.GroupStatus;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.mapper.UserMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.song.CreateSongRequest;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.ChatMessageRepository;
import org.dariusturcu.backend.repository.EmailVerificationTokenRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PasswordResetTokenRepository;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.PlaylistBanRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.RefreshTokenRepository;
import org.dariusturcu.backend.repository.SavedPlaylistRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.TwoFactorBackupCodeRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = UserAccountDeletionIntegrationTest.JpaTestConfig.class)
class UserAccountDeletionIntegrationTest {

    private static final int DEFAULT_RELEASE_YEAR = 2000;
    private static final String YOUTUBE_VIDEO_ID = "dQw4w9WgXcQ";
    private static final String HEX_COLOR = "abcdef";

    @org.springframework.context.annotation.Import({
            org.dariusturcu.backend.history.HistoryDataSourceConfig.class,
            org.dariusturcu.backend.history.GameHistoryService.class
    })
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
                org.dariusturcu.backend.repository.PlayerRepository playerRepository,
                SongRepository songRepository,
                SavedPlaylistRepository savedPlaylistRepository,
                MemberRepository memberRepository,
                GroupRepository groupRepository,
                ChatMessageRepository chatMessageRepository,
                TwoFactorBackupCodeRepository twoFactorBackupCodeRepository,
                RefreshTokenRepository refreshTokenRepository,
                PasswordResetTokenRepository passwordResetTokenRepository,
                EmailVerificationTokenRepository emailVerificationTokenRepository,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new UserService(userRepository, playlistRepository, userMapper, playlistMapper,
                    playlistMembershipRepository, playlistBanRepository, playlistRemovalService, playerRepository,
                    songRepository, savedPlaylistRepository,
                    memberRepository, groupRepository, chatMessageRepository, twoFactorBackupCodeRepository,
                    refreshTokenRepository, passwordResetTokenRepository, emailVerificationTokenRepository,
                    eventPublisher);
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
    private PlaylistRepository playlistRepository;
    @Autowired
    private PlaylistMembershipRepository playlistMembershipRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private SavedPlaylistRepository savedPlaylistRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private TwoFactorBackupCodeRepository twoFactorBackupCodeRepository;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("coreJdbcTemplate")
    private org.springframework.jdbc.core.JdbcTemplate core;
    @Autowired
    private org.dariusturcu.backend.history.GameHistoryService history;
    @Autowired
    private UserService userService;
    @Autowired
    private PlaylistService playlistService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setAuthProvider(AuthProvider.LOCAL);
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
    void deletingAUserClearsAttributionOnTheirSongsAndDeletesAPlaylistTheyAreTheLastMemberOf() {
        User deletingUser = persistUser("deleting-user-" + UUID.randomUUID());
        actAs(deletingUser);

        PlaylistDetailDTO solePlaylist = userService.createPlaylist();
        SongDTO submittedSong = playlistService.createSong(solePlaylist.id(), anySongRequest("Attributed Title"));

        userService.deleteUser();

        assertThat(userRepository.findById(deletingUser.getId())).isEmpty();
        assertThat(playlistRepository.findById(solePlaylist.id())).isEmpty();
        assertThat(songRepository.findById(submittedSong.id()))
                .hasValueSatisfying(song -> assertThat(song.getAddedBy()).isNull());
    }

    @Test
    void deletingAUserWhoOwnsASharedPlaylistTransfersOwnershipInsteadOfDeletingItOrTheOtherMembersData() {
        User owner = persistUser("owner-" + UUID.randomUUID());
        User remainingMember = persistUser("remaining-member-" + UUID.randomUUID());

        actAs(owner);
        PlaylistDetailDTO sharedPlaylist = userService.createPlaylist();
        String inviteCode = playlistRepository.findById(sharedPlaylist.id()).orElseThrow().getInviteCode();

        actAs(remainingMember);
        userService.joinPlaylist(inviteCode, null);

        actAs(owner);
        userService.deleteUser();

        assertThat(userRepository.findById(owner.getId())).isEmpty();
        Optional<Playlist> survivingPlaylist = playlistRepository.findById(sharedPlaylist.id());
        assertThat(survivingPlaylist).isPresent();
        assertThat(survivingPlaylist.get().getOwner().getId()).isEqualTo(remainingMember.getId());
        assertThat(playlistMembershipRepository.existsByPlaylistIdAndUserId(sharedPlaylist.id(), remainingMember.getId()))
                .isTrue();
        assertThat(playlistMembershipRepository.existsByPlaylistIdAndUserId(sharedPlaylist.id(), owner.getId()))
                .isFalse();
    }

    @Test
    void aPreviouslyBannedUserCanDeleteTheirAccount() {
        User playlistOwner = persistUser("ban-owner-" + UUID.randomUUID());
        User bannedUser = persistUser("banned-user-" + UUID.randomUUID());
        actAs(playlistOwner);
        PlaylistDetailDTO ownedPlaylist = userService.createPlaylist();
        core.update("INSERT INTO playlist_bans(playlist_id, user_id) VALUES (?, ?)",
                ownedPlaylist.id(), bannedUser.getId());

        actAs(bannedUser);
        userService.deleteUser();

        assertThat(userRepository.findById(bannedUser.getId())).isEmpty();
        assertThat(core.queryForObject("SELECT count(*) FROM playlist_bans WHERE user_id = ?", Long.class,
                bannedUser.getId())).isZero();
    }

    @Test
    void deletingTheLastMemberOfAPlaylistSavedByOthersAndLinkedToAGroupRemovesThoseReferences() {
        User deletingUser = persistUser("last-member-" + UUID.randomUUID());
        User savingUser = persistUser("saving-user-" + UUID.randomUUID());
        User bannedUser = persistUser("banned-from-sole-" + UUID.randomUUID());
        actAs(deletingUser);
        PlaylistDetailDTO solePlaylist = userService.createPlaylist();
        Playlist playlist = playlistRepository.findById(solePlaylist.id()).orElseThrow();
        playlist.setPublic(true);
        playlistRepository.save(playlist);
        actAs(savingUser);
        playlistService.savePlaylist(solePlaylist.id());
        Group linkedGroup = new Group();
        linkedGroup.setInviteCode(UUID.randomUUID().toString().substring(0, 8));
        linkedGroup.setJoinCode("4321");
        linkedGroup.setStatus(GroupStatus.OPEN);
        linkedGroup.setDjMode(org.dariusturcu.backend.model.group.DjMode.ROTATING);
        linkedGroup.setWinConditionCardCount(10);
        linkedGroup.setCreatedAt(Instant.now());
        Group savedLinkedGroup = groupRepository.save(linkedGroup);
        core.update("INSERT INTO group_playlists(group_id, playlist_id) VALUES (?, ?)",
                savedLinkedGroup.getId(), solePlaylist.id());
        core.update("INSERT INTO playlist_bans(playlist_id, user_id) VALUES (?, ?)",
                solePlaylist.id(), bannedUser.getId());

        actAs(deletingUser);
        userService.deleteUser();

        assertThat(userRepository.findById(deletingUser.getId())).isEmpty();
        assertThat(playlistRepository.findById(solePlaylist.id())).isEmpty();
        assertThat(savedPlaylistRepository.findByUserId(savingUser.getId())).isEmpty();
        assertThat(core.queryForObject("SELECT count(*) FROM group_playlists WHERE playlist_id = ?", Long.class,
                solePlaylist.id())).isZero();
        assertThat(core.queryForObject("SELECT count(*) FROM playlist_bans WHERE playlist_id = ?", Long.class,
                solePlaylist.id())).isZero();
    }

    @Test
    void deletingAnAccountAnonymizesItsSharedHistory() {
        User deletingUser = persistUser("history-owner-" + UUID.randomUUID());
        User remainingUser = persistUser("history-participant-" + UUID.randomUUID());
        long originalSessionId = 101L;
        Long summaryId = core.queryForObject("INSERT INTO game_summaries(source_session_id, group_name, started_at, ended_at, mode, win_target_cards, participant_count, turns_played, ending_reason, rules_version) VALUES (?, 'Group ABCD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'CUSTOM', 8, 2, 4, 'TARGET_REACHED', 'full-pass-v1') RETURNING id", Long.class, originalSessionId);
        for (User participant : java.util.List.of(deletingUser, remainingUser)) {
            core.update("INSERT INTO game_participant_summaries(game_summary_id, user_id, display_name, participation_status, final_card_count, card_rank, artist_rank, title_rank, is_winner, placement_attempts, correct_placements, title_attempts, correct_titles, artist_attempts, correct_artists, bets_placed, bets_won) VALUES (?, ?, ?, 'FINISHED', 8, 1, 1, 1, true, 7, 7, 4, 4, 4, 4, 0, 0)", summaryId, participant.getId(), participant.getUsername());
        }
        actAs(deletingUser);
        userService.deleteUser();
        assertThat(history.export(deletingUser.getId())).isEmpty();
        assertThat(history.export(remainingUser.getId()).getFirst().participants()).anyMatch(participant -> participant.userId() == null && participant.displayName().equals("Deleted player"));
    }

    @Test
    void deletingAUserRemovesAllPersonalDataAcrossPlaylistsGroupsChatHistoryTwoFactorAndAvatar() {
        User deletingUser = persistUser("complete-user-" + UUID.randomUUID());
        byte[] testAvatarBytes = new byte[]{1, 2, 3, 4};
        deletingUser.setAvatarImage(testAvatarBytes);
        deletingUser.setTotpSecret("JBSWY3DPEHPK3PXP");
        deletingUser.setTwoFactorEnabled(true);
        userRepository.save(deletingUser);

        TwoFactorBackupCode backupCode = new TwoFactorBackupCode();
        backupCode.setUser(deletingUser);
        backupCode.setCodeHash("hashed-backup-code");
        backupCode.setUsed(false);
        twoFactorBackupCodeRepository.save(backupCode);

        User playlistOwner = persistUser("playlist-owner-" + UUID.randomUUID());
        actAs(playlistOwner);
        PlaylistDetailDTO publicPlaylist = userService.createPlaylist();
        Playlist playlist = playlistRepository.findById(publicPlaylist.id()).orElseThrow();
        playlist.setPublic(true);
        playlistRepository.save(playlist);
        actAs(deletingUser);
        playlistService.savePlaylist(playlist.getId());

        PlaylistDetailDTO solePlaylist = userService.createPlaylist();
        SongDTO submittedSong = playlistService.createSong(solePlaylist.id(), anySongRequest("Personal Song"));

        Group group = new Group();
        group.setInviteCode(UUID.randomUUID().toString().substring(0, 8));
        group.setJoinCode("1234");
        group.setStatus(GroupStatus.OPEN);
        group.setDjMode(org.dariusturcu.backend.model.group.DjMode.ROTATING);
        group.setWinConditionCardCount(10);
        group.setCreatedAt(Instant.now());
        Group savedGroup = groupRepository.save(group);

        Member member = new Member();
        member.setGroup(savedGroup);
        member.setUser(deletingUser);
        member.setDisplayName(deletingUser.getUsername());
        member.setJoinedAt(Instant.now());
        member.setAdmin(true);
        member.setConnected(true);
        member.setInVoice(false);
        memberRepository.save(member);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setGroup(savedGroup);
        chatMessage.setSender(deletingUser);
        chatMessage.setContent("Hello group from deleting user");
        chatMessage.setCreatedAt(Instant.now());
        chatMessageRepository.save(chatMessage);

        long originalSessionId = 202L;
        Long summaryId = core.queryForObject(
                "INSERT INTO game_summaries(source_session_id, group_name, started_at, ended_at, mode, win_target_cards, participant_count, turns_played, ending_reason, rules_version) VALUES (?, 'Group Full', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'CUSTOM', 8, 1, 2, 'TARGET_REACHED', 'full-pass-v1') RETURNING id",
                Long.class, originalSessionId);
        core.update("INSERT INTO game_participant_summaries(game_summary_id, user_id, display_name, participation_status, final_card_count, card_rank, artist_rank, title_rank, is_winner, placement_attempts, correct_placements, title_attempts, correct_titles, artist_attempts, correct_artists, bets_placed, bets_won) VALUES (?, ?, ?, 'FINISHED', 8, 1, 1, 1, true, 5, 5, 2, 2, 2, 2, 0, 0)",
                summaryId, deletingUser.getId(), deletingUser.getUsername());

        userService.deleteUser();

        assertThat(userRepository.findById(deletingUser.getId())).isEmpty();
        assertThat(twoFactorBackupCodeRepository.findByUserAndUsedFalse(deletingUser)).isEmpty();
        assertThat(savedPlaylistRepository.findByUserId(deletingUser.getId())).isEmpty();
        assertThat(memberRepository.findByUser(deletingUser)).isEmpty();
        assertThat(chatMessageRepository.findBySenderIdOrderByCreatedAtAsc(deletingUser.getId())).isEmpty();
        assertThat(playlistRepository.findById(solePlaylist.id())).isEmpty();
        assertThat(songRepository.findById(submittedSong.id()))
                .hasValueSatisfying(song -> assertThat(song.getAddedBy()).isNull());
        assertThat(history.export(deletingUser.getId())).isEmpty();
    }
}
