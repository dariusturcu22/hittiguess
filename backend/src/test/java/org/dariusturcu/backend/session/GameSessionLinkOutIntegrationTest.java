package org.dariusturcu.backend.session;

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
import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundLinkOutDTO;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
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
import org.dariusturcu.backend.service.PlaylistAccessService;
import org.dariusturcu.backend.service.PlaylistExpansionService;
import org.dariusturcu.backend.service.PendingSessionSongPool;
import org.dariusturcu.backend.service.SessionResultsStore;
import org.dariusturcu.backend.difficulty.AggregateBaselinePredictor;
import org.dariusturcu.backend.difficulty.DifficultyBand;
import org.dariusturcu.backend.difficulty.DifficultyTunedSongSelector;
import org.dariusturcu.backend.difficulty.GroupDifficultyStrategy;
import org.dariusturcu.backend.difficulty.SongDifficultyScorer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
import org.springframework.web.client.RestClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the DJ link-out access path against a real Postgres instance: the round's DJ can
 * fetch the current song's YouTube watch URL, a non-DJ player and a non-member are both
 * denied, and the link stops being fetchable once the round is revealed.
 */
@Testcontainers
@SpringBootTest(classes = GameSessionLinkOutIntegrationTest.JpaTestConfig.class)
@Transactional
class GameSessionLinkOutIntegrationTest {

    private static final String DISTINCT_YOUTUBE_ID = "dQw4w9WgXcQ";

    static class ImmediateTaskScheduler implements TaskScheduler {
        @Override
        public java.util.concurrent.ScheduledFuture<?> schedule(Runnable task, org.springframework.scheduling.Trigger trigger) {
            task.run();
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            task.run();
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, java.time.Duration delay) {
            task.run();
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable task, Instant startTime, java.time.Duration delay) {
            task.run();
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(Runnable task, java.time.Duration period) {
            task.run();
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(
                Runnable task, Instant startTime, java.time.Duration period) {
            task.run();
            return null;
        }
    }

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
        SessionMapper sessionMapper(BetRepository betRepository) {
            return new SessionMapper(betRepository);
        }

        @Bean
        SessionResultsStore sessionResultsStore() {
            return new SessionResultsStore();
        }

        @Bean
        TaskScheduler taskScheduler() {
            return new ImmediateTaskScheduler();
        }

        @Bean
        GameSessionScheduler gameSessionScheduler(TaskScheduler taskScheduler) {
            return new GameSessionScheduler(taskScheduler);
        }

        @Bean
        GroupService groupService(GroupRepository groupRepository, MemberRepository memberRepository,
                                   PlaylistRepository playlistRepository, GroupMapper groupMapper,
                                   ApplicationEventPublisher eventPublisher, PlaylistAccessService playlistAccessService) {
            return new GroupService(groupRepository, memberRepository, playlistRepository, groupMapper, eventPublisher, playlistAccessService);
        }

        @Bean
        PendingSessionSongPool pendingSessionSongPool() {
            return new PendingSessionSongPool();
        }

        @Bean
        DifficultyTunedSongSelector difficultySelector(SongRepository songRepository, RoundRepository roundRepository) {
            return new DifficultyTunedSongSelector(
                    songRepository, roundRepository, new SongDifficultyScorer(), new DifficultyBand(),
                    new GroupDifficultyStrategy(), new AggregateBaselinePredictor());
        }

        @Bean
        PlaylistExpansionService playlistExpansionService() {
            return new PlaylistExpansionService(RestClient.create());
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theRoundDjCanFetchTheCurrentSongsWatchUrl() {
        GameSession session = startTwoPlayerRotatingSession("linkout-dj-fetch");
        Round round = firstRound(session);
        Player djPlayer = round.getDjPlayer();

        RoundLinkOutDTO linkOut = gameSessionService.getCurrentRoundLinkOut(session.getId(), djPlayer.getUser().getId());

        assertThat(linkOut.roundId()).isEqualTo(round.getId());
        assertThat(linkOut.roundNumber()).isEqualTo(round.getRoundNumber());
        assertThat(linkOut.youtubeId()).isEqualTo(round.getSong().getYoutubeId());
        assertThat(linkOut.watchUrl()).isEqualTo("https://www.youtube.com/watch?v=" + round.getSong().getYoutubeId());
    }

    @Test
    void aNonDjPlayerIsDeniedTheLinkOut() {
        GameSession session = startTwoPlayerRotatingSession("linkout-non-dj");
        Round round = firstRound(session);
        Player nonDjPlayer = session.getPlayers().stream()
                .filter(player -> !player.getId().equals(round.getDjPlayer().getId()))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> gameSessionService.getCurrentRoundLinkOut(session.getId(), nonDjPlayer.getUser().getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aNonMemberIsDeniedTheLinkOut() {
        GameSession session = startTwoPlayerRotatingSession("linkout-non-member");
        User outsider = persistUser("linkout-outsider-" + System.nanoTime());

        assertThatThrownBy(() -> gameSessionService.getCurrentRoundLinkOut(session.getId(), outsider.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theLinkOutIsNoLongerFetchableOnceTheRoundIsRevealed() {
        GameSession session = startTwoPlayerRotatingSession("linkout-after-reveal");
        Round round = firstRound(session);
        Long djUserId = round.getDjPlayer().getUser().getId();

        round.setStatus(RoundStatus.REVEALED);
        round.setRevealedAt(Instant.now());
        roundRepository.save(round);

        assertThatThrownBy(() -> gameSessionService.getCurrentRoundLinkOut(session.getId(), djUserId))
                .isInstanceOf(ConflictException.class);
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

    private Song persistSong(Playlist playlist, User addedBy, int releaseYear, String title) {
        Song song = new Song();
        song.setTitle(title);
        song.setReleaseYear(releaseYear);
        song.setYoutubeId(DISTINCT_YOUTUBE_ID);
        playlist.addSong(song);
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

    private Round firstRound(GameSession session) {
        return roundRepository.findTopBySessionOrderByRoundNumberDesc(session).orElseThrow();
    }

    private GameSession startTwoPlayerRotatingSession(String label) {
        User admin = persistUser(label + "-admin-" + System.nanoTime());

        authenticateAs(admin);
        GroupDetailDTO createdGroup = groupService.createGroup(new CreateGroupRequest(null, null));

        User otherPlayer = persistUser(label + "-player-" + System.nanoTime());
        authenticateAs(otherPlayer);
        groupService.joinGroup(new JoinGroupRequest(createdGroup.inviteCode(), null, null, null));

        Playlist playlist = new Playlist();
        playlist.setName(label + " Playlist");
        playlist.setColor("445566");
        playlist.setInviteCode(label + "-playlist-" + System.nanoTime());
        playlist.setOwner(admin);
        Playlist savedPlaylist = playlistRepository.save(playlist);
        int minimumWinConditionCardCount = 5;
        for (int songIndex = 0; songIndex < 10; songIndex++) {
            persistSong(savedPlaylist, admin, 1960 + songIndex, label + " Song " + songIndex);
        }

        authenticateAs(admin);
        groupService.updateGroupSettings(createdGroup.id(), new UpdateGroupSettingsRequest(
                Set.of(savedPlaylist.getId()), DjMode.ROTATING, minimumWinConditionCardCount));
        groupService.startGameSession(createdGroup.id());

        return gameSessionRepository.findByGroupId(createdGroup.id()).orElseThrow();
    }
}
