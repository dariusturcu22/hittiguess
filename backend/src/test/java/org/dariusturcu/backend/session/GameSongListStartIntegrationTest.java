package org.dariusturcu.backend.session;

import org.dariusturcu.backend.difficulty.AggregateBaselinePredictor;
import org.dariusturcu.backend.difficulty.DifficultyBand;
import org.dariusturcu.backend.difficulty.DifficultyTier;
import org.dariusturcu.backend.difficulty.DifficultyTunedSongSelector;
import org.dariusturcu.backend.difficulty.GroupDifficultyStrategy;
import org.dariusturcu.backend.difficulty.SongDifficultyScorer;
import org.dariusturcu.backend.exception.ConflictException;
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
import org.dariusturcu.backend.repository.StoredSessionResultsRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.scheduling.GameSessionScheduler;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GameSessionService;
import org.dariusturcu.backend.service.PlacementPreviewGate;
import org.dariusturcu.backend.service.GameSessionStartListener;
import org.dariusturcu.backend.service.GroupService;
import org.dariusturcu.backend.service.PlaylistAccessService;
import org.dariusturcu.backend.service.PlaylistExpansionService;
import org.dariusturcu.backend.service.SessionResultsStore;
import tools.jackson.databind.json.JsonMapper;

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
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers how a Difficulty-Based start fills its song pool against a real Postgres instance:
 * when the requested tier band is short the selection widens through the other prepared tiers
 * rather than failing, a catalog too small overall still fails with the real counts in the
 * message, and a start never writes a playlist row. Runs in its own container so a committing
 * start elsewhere cannot leak catalog songs into these counts.
 */
@Testcontainers
@SpringBootTest(classes = GameSongListStartIntegrationTest.JpaTestConfig.class)
@Transactional
class GameSongListStartIntegrationTest {

    private static final int TWO_PLAYER_WIN_CONDITION_CARD_COUNT = 5;
    private static final int TARGET_POOL_SIZE = 30;
    private static final int SHORT_EASY_BAND_SIZE = 2;
    private static final int MEDIUM_FILL_SIZE = TARGET_POOL_SIZE - SHORT_EASY_BAND_SIZE;
    private static final int TOO_SMALL_CATALOG_SIZE = 4;
    private static final int BASE_RELEASE_YEAR = 1950;
    private static final Integer EASY_SITELINKS = 40;
    private static final Integer MEDIUM_SITELINKS = 20;
    private static final double PREPARED_SCORE = 0.5;
    private static final String PREPARED_CALCULATION_VERSION = "test-v1";

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
            return Mockito.mock(PlaylistExpansionService.class);
        }

        @Bean
        SessionMapper sessionMapper(BetRepository betRepository) {
            return new SessionMapper(betRepository);
        }

        @Bean
        SessionResultsStore sessionResultsStore(StoredSessionResultsRepository storedSessionResultsRepository) {
            return new SessionResultsStore(storedSessionResultsRepository, JsonMapper.builder().build());
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
        GroupService groupService(GroupRepository groupRepository, MemberRepository memberRepository,
                GameSessionRepository gameSessionRepository, PlaylistRepository playlistRepository,
                GroupMapper groupMapper, ApplicationEventPublisher eventPublisher,
                PlaylistAccessService playlistAccessService) {
            return new GroupService(groupRepository, memberRepository, gameSessionRepository, playlistRepository,
                    groupMapper, eventPublisher, playlistAccessService);
        }

        @Bean
        org.dariusturcu.backend.difficulty.PreparedDifficultyService preparedDifficultyService(
                javax.sql.DataSource dataSource, SongRepository songs) {
            return new org.dariusturcu.backend.difficulty.PreparedDifficultyService(
                    new JdbcTemplate(dataSource), Mockito.mock(JdbcTemplate.class), songs,
                    new SongDifficultyScorer(), new DifficultyBand());
        }

        @Bean
        DifficultyTunedSongSelector difficultySelector(SongRepository songRepository, RoundRepository roundRepository,
                org.dariusturcu.backend.difficulty.PreparedDifficultyService prepared) {
            return new DifficultyTunedSongSelector(prepared, songRepository, roundRepository, new SongDifficultyScorer(),
                    new DifficultyBand(), new GroupDifficultyStrategy(), new AggregateBaselinePredictor());
        }

        @Bean
        GameSessionService gameSessionService(GameSessionRepository gameSessionRepository,
                PlayerRepository playerRepository, RoundRepository roundRepository, GuessRepository guessRepository,
                BetRepository betRepository, GroupRepository groupRepository, SongRepository songRepository,
                PlaylistRepository playlistRepository, GroupService groupService,
                PlaylistAccessService playlistAccessService, PlaylistExpansionService playlistExpansionService,
                DifficultyTunedSongSelector difficultySelector,                 SessionMapper sessionMapper, SessionResultsStore resultsStore,
                GameSessionScheduler gameSessionScheduler, ApplicationEventPublisher eventPublisher,
                PlacementPreviewGate placementPreviewGate) {
            return new GameSessionService(gameSessionRepository, playerRepository, roundRepository, guessRepository,
                    betRepository, groupRepository, songRepository, playlistRepository, groupService,
                    playlistAccessService, playlistExpansionService, difficultySelector, sessionMapper,
                    resultsStore, gameSessionScheduler, eventPublisher, new org.dariusturcu.backend.service.GameSongList(),
                    placementPreviewGate);
        }

        @Bean
        PlacementPreviewGate placementPreviewGate() {
            return new PlacementPreviewGate();
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
    private javax.sql.DataSource dataSource;

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

    private Playlist playlistWithSongs(String label, User owner, int songCount, Integer sitelinksCount) {
        Playlist playlist = new Playlist();
        playlist.setName(label + " Playlist");
        playlist.setColor("778899");
        playlist.setInviteCode(label + "-playlist-" + System.nanoTime());
        playlist.setOwner(owner);
        Playlist savedPlaylist = playlistRepository.save(playlist);
        for (int songIndex = 0; songIndex < songCount; songIndex++) {
            int releaseYear = BASE_RELEASE_YEAR + songIndex;
            Song song = new Song();
            song.setTitle(label + " Song " + songIndex);
            song.setReleaseYear(releaseYear);
            song.setYoutubeId(label + "-video-" + songIndex);
            song.setVerificationStatus(VerificationStatus.VERIFIED);
            song.setWikidataSitelinksCount(sitelinksCount);
            savedPlaylist.addSong(song);
            song.setAddedBy(owner);
            Song savedSong = songRepository.save(song);

            SongArtist artist = new SongArtist();
            artist.setSong(savedSong);
            artist.setName("Artist " + releaseYear);
            artist.setRole(ArtistRole.MAIN);
            artist.setDisplayOrder(0);
            savedSong.getArtists().add(artist);
            songRepository.save(savedSong);
        }
        return savedPlaylist;
    }

    private GroupDetailDTO twoPlayerGroup(User admin, User other, int winConditionCardCount) {
        authenticateAs(admin);
        GroupDetailDTO createdGroup = groupService.createGroup(new CreateGroupRequest(null, null));
        authenticateAs(other);
        groupService.joinGroup(new JoinGroupRequest(createdGroup.inviteCode(), null, null, null));

        authenticateAs(admin);
        groupService.updateGroupSettings(createdGroup.id(), new UpdateGroupSettingsRequest(
                Set.of(), DjMode.ROTATING, winConditionCardCount, null));
        return createdGroup;
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

    private Set<Long> songIdsOf(Playlist playlist) {
        Set<Long> ids = new HashSet<>();
        playlist.getSongs().forEach(song -> ids.add(song.getId()));
        return ids;
    }

    private void prepareDifficultyRows(Playlist playlist, DifficultyTier tier) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (Song song : playlist.getSongs()) {
            jdbcTemplate.update("""
                    INSERT INTO song_difficulty(song_id, score, tier, calculation_version, calculated_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(song_id) DO UPDATE SET tier = EXCLUDED.tier
                    """, song.getId(), PREPARED_SCORE, tier.name(), PREPARED_CALCULATION_VERSION);
        }
    }

    @Test
    void difficultyStartWidensAcrossTiersWhenTheRequestedBandIsShort() {
        User admin = persistUser("widening-admin-" + System.nanoTime());
        User other = persistUser("widening-player-" + System.nanoTime());
        Playlist shortBand = playlistWithSongs("widening-easy", admin, SHORT_EASY_BAND_SIZE, EASY_SITELINKS);
        Playlist otherTiers = playlistWithSongs("widening-medium", admin, MEDIUM_FILL_SIZE, MEDIUM_SITELINKS);
        prepareDifficultyRows(shortBand, DifficultyTier.EASY);
        prepareDifficultyRows(otherTiers, DifficultyTier.MEDIUM);
        GroupDetailDTO group = twoPlayerGroup(admin, other, TWO_PLAYER_WIN_CONDITION_CARD_COUNT);
        groupService.updateGroupSettings(group.id(),
                new UpdateGroupSettingsRequest(null, null, null, null, DifficultyTier.EASY));
        songRepository.flush();

        Set<Long> expectedSongIds = new HashSet<>(songIdsOf(shortBand));
        expectedSongIds.addAll(songIdsOf(otherTiers));

        groupService.startGameSession(group.id());

        GameSession session = gameSessionRepository.findByGroupId(group.id()).orElseThrow();
        assertThat(session.getDifficultyTier()).isEqualTo(DifficultyTier.EASY);
        assertThat(playedSongIds(session)).containsExactlyInAnyOrderElementsOf(expectedSongIds);
    }

    @Test
    void difficultyStartKeepsTheRequestedTierWhenItsBandIsLargeEnough() {
        User admin = persistUser("dominant-admin-" + System.nanoTime());
        User other = persistUser("dominant-player-" + System.nanoTime());
        Playlist requestedTier = playlistWithSongs("dominant-easy", admin, TARGET_POOL_SIZE, EASY_SITELINKS);
        Playlist otherTiers = playlistWithSongs("dominant-medium", admin, MEDIUM_FILL_SIZE, MEDIUM_SITELINKS);
        prepareDifficultyRows(requestedTier, DifficultyTier.EASY);
        prepareDifficultyRows(otherTiers, DifficultyTier.MEDIUM);
        GroupDetailDTO group = twoPlayerGroup(admin, other, TWO_PLAYER_WIN_CONDITION_CARD_COUNT);
        groupService.updateGroupSettings(group.id(),
                new UpdateGroupSettingsRequest(null, null, null, null, DifficultyTier.EASY));
        songRepository.flush();

        groupService.startGameSession(group.id());

        GameSession session = gameSessionRepository.findByGroupId(group.id()).orElseThrow();
        assertThat(playedSongIds(session)).containsExactlyInAnyOrderElementsOf(songIdsOf(requestedTier));
    }

    @Test
    void difficultyStartFailsWithTheRealNumbersWhenTheWholeCatalogIsTooSmall() {
        User admin = persistUser("too-small-admin-" + System.nanoTime());
        User other = persistUser("too-small-player-" + System.nanoTime());
        playlistWithSongs("too-small", admin, TOO_SMALL_CATALOG_SIZE, EASY_SITELINKS);
        GroupDetailDTO group = twoPlayerGroup(admin, other, TWO_PLAYER_WIN_CONDITION_CARD_COUNT);
        groupService.updateGroupSettings(group.id(),
                new UpdateGroupSettingsRequest(null, null, null, null, DifficultyTier.EASY));
        songRepository.flush();

        assertThatThrownBy(() -> groupService.startGameSession(group.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(TOO_SMALL_CATALOG_SIZE + " available, " + TARGET_POOL_SIZE + " needed");
    }

    @Test
    void startingASessionCreatesNoPlaylistRows() {
        User admin = persistUser("playlist-count-admin-" + System.nanoTime());
        User other = persistUser("playlist-count-player-" + System.nanoTime());
        playlistWithSongs("playlist-count-easy", admin, SHORT_EASY_BAND_SIZE, EASY_SITELINKS);
        playlistWithSongs("playlist-count-medium", admin, MEDIUM_FILL_SIZE, MEDIUM_SITELINKS);
        GroupDetailDTO group = twoPlayerGroup(admin, other, TWO_PLAYER_WIN_CONDITION_CARD_COUNT);
        groupService.updateGroupSettings(group.id(),
                new UpdateGroupSettingsRequest(null, null, null, null, DifficultyTier.EASY));
        songRepository.flush();
        long playlistsBeforeStart = playlistRepository.count();

        groupService.startGameSession(group.id());

        assertThat(playlistRepository.count()).isEqualTo(playlistsBeforeStart);
    }
}
