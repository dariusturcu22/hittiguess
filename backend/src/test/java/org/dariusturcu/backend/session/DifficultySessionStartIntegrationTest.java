package org.dariusturcu.backend.session;

import org.dariusturcu.backend.difficulty.AggregateBaselinePredictor;
import org.dariusturcu.backend.difficulty.DifficultyBand;
import org.dariusturcu.backend.difficulty.DifficultyTier;
import org.dariusturcu.backend.difficulty.DifficultyTunedSongSelector;
import org.dariusturcu.backend.difficulty.GroupDifficultyStrategy;
import org.dariusturcu.backend.difficulty.SongDifficultyScorer;
import org.dariusturcu.backend.model.group.CreateGroupRequest;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.JoinGroupRequest;
import org.dariusturcu.backend.model.group.UpdateGroupSettingsRequest;
import org.dariusturcu.backend.model.mapper.GroupMapper;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SessionMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.GenerateDifficultySetRequest;
import org.dariusturcu.backend.model.session.GeneratedSongPreviewDTO;
import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.StartCustomSessionRequest;
import org.dariusturcu.backend.model.session.StartSessionWithSongsRequest;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.BetRepository;
import org.dariusturcu.backend.repository.GameSessionRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.GuessRepository;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PlayerRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.RoundRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.scheduling.GameSessionScheduler;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GameSessionService;
import org.dariusturcu.backend.service.GameSessionStartListener;
import org.dariusturcu.backend.service.GroupService;
import org.dariusturcu.backend.service.PendingSessionSongPool;
import org.dariusturcu.backend.service.PlaylistAccessService;
import org.dariusturcu.backend.service.PlaylistExpansionService;
import org.dariusturcu.backend.service.SessionResultsStore;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Difficulty-Based and Custom-mode session starts end to end against a real
 * Postgres instance: generate-for-review then confirm plays exactly the reviewed set, a
 * custom start from an accessible playlist plays that playlist's songs, and a custom
 * start from a pasted link skips videos with no catalog song. The link expansion is
 * stubbed at the service boundary: the real implementation calls the AI microservice
 * over HTTP, unreachable from a repository test.
 */
@Testcontainers
@SpringBootTest(classes = DifficultySessionStartIntegrationTest.JpaTestConfig.class)
@Transactional
class DifficultySessionStartIntegrationTest {

    private static final int REVIEWED_CARD_COUNT = 6;
    private static final int FULL_GROUP_EXTRA_MEMBER_COUNT = 7;
    private static final int FULL_GROUP_CATALOG_SONG_COUNT = 24;
    private static final int FULL_GROUP_TARGET_CARD_COUNT = 8;
    private static final Duration GENERATION_TIME_BUDGET = Duration.ofSeconds(1);

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = GroupRepository.class)
    static class JpaTestConfig {
        @Bean
        SongMapper songMapper() {
            return new SongMapper();
        }

        @Bean
        PlaylistMapper playlistMapper() {
            return new PlaylistMapper(null);
        }

        @Bean
        GroupMapper groupMapper(PlaylistMapper playlistMapper) {
            return new GroupMapper(playlistMapper);
        }

        @Bean
        PlaylistAccessService playlistAccessService(PlaylistMembershipRepository playlistMembershipRepository) {
            return new PlaylistAccessService(playlistMembershipRepository);
        }

        @Bean
        PlaylistExpansionService playlistExpansionService() {
            return new PlaylistExpansionService(null) {
                @Override
                public List<String> expandPlaylist(String playlistUrlOrId) {
                    return List.of("known-video-1", "known-video-2", "known-video-3", "ghost-video");
                }
            };
        }

        @Bean
        SessionMapper sessionMapper(BetRepository betRepository) {
            return new SessionMapper(betRepository);
        }

        @Bean
        SessionResultsStore sessionResultsStore() {
            return new SessionResultsStore();
        }

        @Bean
        PendingSessionSongPool pendingSessionSongPool() {
            return new PendingSessionSongPool();
        }

        @Bean
        TaskScheduler taskScheduler() {
            return Mockito.mock(TaskScheduler.class);
        }

        @Bean
        GameSessionScheduler gameSessionScheduler(TaskScheduler taskScheduler) {
            return new GameSessionScheduler(taskScheduler);
        }

        @Bean
        GroupService groupService(GroupRepository groupRepository, MemberRepository memberRepository, GameSessionRepository gameSessionRepository,
                                  PlaylistRepository playlistRepository, GroupMapper groupMapper,
                                  ApplicationEventPublisher eventPublisher, PlaylistAccessService playlistAccessService) {
            return new GroupService(groupRepository, memberRepository, gameSessionRepository, playlistRepository, groupMapper, eventPublisher, playlistAccessService);
        }

        @Bean
        DifficultyTunedSongSelector difficultySelector(SongRepository songRepository, RoundRepository roundRepository) {
            return new DifficultyTunedSongSelector(
                    songRepository, roundRepository, new SongDifficultyScorer(), new DifficultyBand(),
                    new GroupDifficultyStrategy(), new AggregateBaselinePredictor());
        }

        @Bean
        GameSessionService gameSessionService(
                GameSessionRepository gameSessionRepository, PlayerRepository playerRepository,
                RoundRepository roundRepository, GuessRepository guessRepository, BetRepository betRepository,
                GroupRepository groupRepository, SongRepository songRepository, PlaylistRepository playlistRepository,
                GroupService groupService, PlaylistAccessService playlistAccessService,
                PlaylistExpansionService playlistExpansionService, DifficultyTunedSongSelector difficultySelector,
                PendingSessionSongPool pendingPool, SessionMapper sessionMapper, SessionResultsStore resultsStore,
                GameSessionScheduler gameSessionScheduler, ApplicationEventPublisher eventPublisher) {
            return new GameSessionService(gameSessionRepository, playerRepository, roundRepository, guessRepository,
                    betRepository, groupRepository, songRepository, playlistRepository, groupService,
                    playlistAccessService, playlistExpansionService, difficultySelector, pendingPool, sessionMapper,
                    resultsStore, gameSessionScheduler, eventPublisher);
        }

        @Bean
        GameSessionStartListener gameSessionStartListener(GameSessionService gameSessionService) {
            return new GameSessionStartListener(gameSessionService);
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> false);
    }

    @BeforeAll
    static void migrate() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Autowired
    private GroupService groupService;
    @Autowired
    private GameSessionService gameSessionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private GameSessionRepository gameSessionRepository;
    @Autowired
    private RoundRepository roundRepository;
    @Autowired
    private PendingSessionSongPool pendingPool;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setRole(Role.USER);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setImageUrl(username + "-avatar.png");
        return userRepository.save(user);
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    private Song persistCatalogSong(Playlist playlist, User addedBy, int releaseYear, String title,
                                    VerificationStatus status, Integer sitelinksCount, String youtubeId) {
        Song song = new Song();
        song.setTitle(title);
        song.setReleaseYear(releaseYear);
        song.setYoutubeId(youtubeId);
        song.setVerificationStatus(status);
        song.setWikidataSitelinksCount(sitelinksCount);
        if (playlist != null) {
            playlist.addSong(song);
        }
        song.setAddedBy(addedBy);
        Song savedSong = songRepository.save(song);

        SongArtist artist = new SongArtist();
        artist.setSong(savedSong);
        artist.setName("Artist " + releaseYear);
        artist.setRole(ArtistRole.MAIN);
        artist.setDisplayOrder(0);
        savedSong.getArtists().add(artist);
        return songRepository.save(savedSong);
    }

    private GroupDetailDTO twoPlayerGroup(User admin, User other, Playlist playlist, int winConditionCardCount) {
        authenticateAs(admin);
        GroupDetailDTO createdGroup = groupService.createGroup(new CreateGroupRequest(null, null));
        authenticateAs(other);
        groupService.joinGroup(new JoinGroupRequest(createdGroup.inviteCode(), null, null, null));

        authenticateAs(admin);
        groupService.updateGroupSettings(createdGroup.id(), new UpdateGroupSettingsRequest(
                playlist == null ? Set.of() : Set.of(playlist.getId()), DjMode.ROTATING, winConditionCardCount, null));
        return createdGroup;
    }

    private Playlist playlistWithSongs(String label, User owner, int songCount, VerificationStatus status,
                                       Integer sitelinksCount) {
        Playlist playlist = new Playlist();
        playlist.setName(label + " Playlist");
        playlist.setColor("778899");
        playlist.setInviteCode(label + "-playlist-" + System.nanoTime());
        playlist.setOwner(owner);
        Playlist savedPlaylist = playlistRepository.save(playlist);
        for (int songIndex = 0; songIndex < songCount; songIndex++) {
            persistCatalogSong(savedPlaylist, owner, 1950 + songIndex, label + " Song " + songIndex,
                    status, sitelinksCount, label + "-video-" + songIndex);
        }
        return savedPlaylist;
    }

    private Set<Long> playedSongIds(GameSession session) {
        Set<Long> playedIds = new HashSet<>();
        session.getPlayers().forEach(player ->
                player.getTimeline().forEach(card -> playedIds.add(card.getSong().getId())));
        playedIds.addAll(session.getSongQueue());
        roundRepository.findTopBySessionOrderByRoundNumberDesc(session)
                .ifPresent(round -> playedIds.add(round.getSong().getId()));
        return playedIds;
    }

    @Test
    void difficultyGenerateForAFullGroupReturnsInWellUnderASecond() {
        User admin = persistUser("perf-admin-" + System.nanoTime());
        Playlist playlist = playlistWithSongs("perf", admin, FULL_GROUP_CATALOG_SONG_COUNT,
                VerificationStatus.VERIFIED, 30);

        authenticateAs(admin);
        GroupDetailDTO createdGroup = groupService.createGroup(new CreateGroupRequest(null, null));
        for (int memberIndex = 0; memberIndex < FULL_GROUP_EXTRA_MEMBER_COUNT; memberIndex++) {
            User member = persistUser("perf-member-" + memberIndex + "-" + System.nanoTime());
            authenticateAs(member);
            groupService.joinGroup(new JoinGroupRequest(createdGroup.inviteCode(), null, null, null));
        }

        authenticateAs(admin);
        groupService.updateGroupSettings(createdGroup.id(), new UpdateGroupSettingsRequest(
                Set.of(playlist.getId()), DjMode.ROTATING, 5, null));

        Instant generationStartedAt = Instant.now();
        List<GeneratedSongPreviewDTO> previews = gameSessionService.generateDifficultySet(
                createdGroup.id(), new GenerateDifficultySetRequest(DifficultyTier.EASY, FULL_GROUP_TARGET_CARD_COUNT));
        Duration generationElapsed = Duration.between(generationStartedAt, Instant.now());

        assertThat(previews).hasSize(FULL_GROUP_TARGET_CARD_COUNT);
        assertThat(generationElapsed).isLessThan(GENERATION_TIME_BUDGET);
    }

    @Test
    void difficultyGenerateThenStartPlaysExactlyTheReviewedSet() {
        User admin = persistUser("difficulty-admin-" + System.nanoTime());
        User other = persistUser("difficulty-player-" + System.nanoTime());
        Playlist playlist = playlistWithSongs("difficulty", admin, 8, VerificationStatus.VERIFIED, 30);
        GroupDetailDTO createdGroup = twoPlayerGroup(admin, other, playlist, 5);

        authenticateAs(admin);
        List<GeneratedSongPreviewDTO> previews = gameSessionService.generateDifficultySet(
                createdGroup.id(), new GenerateDifficultySetRequest(DifficultyTier.EASY, REVIEWED_CARD_COUNT));
        assertThat(previews).hasSize(REVIEWED_CARD_COUNT);

        List<Long> reviewedIds = previews.stream().map(GeneratedSongPreviewDTO::id).toList();
        gameSessionService.startSessionWithSongs(createdGroup.id(), new StartSessionWithSongsRequest(reviewedIds));

        GameSession session = gameSessionRepository.findByGroupId(createdGroup.id()).orElseThrow();
        assertThat(session.getPlayers()).hasSize(2);
        assertThat(playedSongIds(session)).containsExactlyInAnyOrderElementsOf(reviewedIds);
        assertThat(pendingPool.take(createdGroup.id())).isEmpty();
    }

    @Test
    void customStartFromPlaylistPlaysThatPlaylistsSongs() {
        User admin = persistUser("custom-admin-" + System.nanoTime());
        User other = persistUser("custom-player-" + System.nanoTime());
        Playlist playlist = playlistWithSongs("custom", admin, 6, VerificationStatus.UNVERIFIED, null);
        GroupDetailDTO createdGroup = twoPlayerGroup(admin, other, playlist, 5);
        List<Long> playlistSongIds = new ArrayList<>(playlist.getSongs().stream().map(Song::getId).toList());

        authenticateAs(admin);
        gameSessionService.startCustomSession(createdGroup.id(), new StartCustomSessionRequest(playlist.getId(), null));

        GameSession session = gameSessionRepository.findByGroupId(createdGroup.id()).orElseThrow();
        assertThat(playedSongIds(session)).containsExactlyInAnyOrderElementsOf(playlistSongIds);
    }

    @Test
    void customStartFromPastedLinkSkipsVideosWithNoCatalogSong() {
        User admin = persistUser("link-admin-" + System.nanoTime());
        User other = persistUser("link-player-" + System.nanoTime());
        List<Long> knownIds = List.of(
                persistCatalogSong(null, admin, 1960, "Known One", VerificationStatus.VERIFIED, 30, "known-video-1").getId(),
                persistCatalogSong(null, admin, 1961, "Known Two", VerificationStatus.VERIFIED, 30, "known-video-2").getId(),
                persistCatalogSong(null, admin, 1962, "Known Three", VerificationStatus.VERIFIED, 30, "known-video-3").getId());
        GroupDetailDTO createdGroup = twoPlayerGroup(admin, other, null, 5);

        authenticateAs(admin);
        gameSessionService.startCustomSession(
                createdGroup.id(), new StartCustomSessionRequest(null, "https://youtube.com/playlist?list=stubbed"));

        GameSession session = gameSessionRepository.findByGroupId(createdGroup.id()).orElseThrow();
        assertThat(playedSongIds(session)).containsExactlyInAnyOrderElementsOf(knownIds);
    }
}
