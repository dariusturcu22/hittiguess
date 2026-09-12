package org.dariusturcu.backend.session;

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
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.GameSessionRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.GuessRepository;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PlayerRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.RoundRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.scheduling.GameSessionScheduler;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GameSessionService;
import org.dariusturcu.backend.service.GameSessionStartListener;
import org.dariusturcu.backend.service.GroupService;
import org.dariusturcu.backend.service.SessionResultsStore;

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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves betting is concurrency-safe against a real Postgres instance and real threads,
 * not a single-threaded simulation: several threads call GameSessionService.placeBet
 * against the very same round simultaneously, and exactly one of them succeeds.
 *
 * Deliberately not class-level @Transactional, unlike GameSessionLifecycleIntegrationTest:
 * the round this test races bets against must actually be committed and visible to other
 * real database connections before the concurrent threads (each running the service's own
 * @Transactional method, on their own connection) start, which a single rolled-back test
 * transaction on one connection would not guarantee. Uses a real ThreadPoolTaskScheduler
 * rather than an immediate-fire test double: the round is put into BETTING by direct
 * repository manipulation instead of by waiting out the real countdown/lock-in flow, so no
 * scheduled effect needs to fire at all for this test to exercise the real race.
 */
@Testcontainers
@SpringBootTest(classes = GameSessionBettingConcurrencyIntegrationTest.JpaTestConfig.class)
class GameSessionBettingConcurrencyIntegrationTest {

    private static final int CONCURRENT_ATTEMPTS_PER_ELIGIBLE_BETTOR = 2;
    private static final int CONCURRENCY_TEST_TIMEOUT_SECONDS = 10;

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
            return new PlaylistMapper(null, null);
        }

        @Bean
        GroupMapper groupMapper(PlaylistMapper playlistMapper) {
            return new GroupMapper(playlistMapper);
        }

        @Bean
        SessionMapper sessionMapper() {
            return new SessionMapper();
        }

        @Bean
        SessionResultsStore sessionResultsStore() {
            return new SessionResultsStore();
        }

        @Bean
        ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.initialize();
            return scheduler;
        }

        @Bean
        GameSessionScheduler gameSessionScheduler(ThreadPoolTaskScheduler taskScheduler) {
            return new GameSessionScheduler(taskScheduler);
        }

        @Bean
        GroupService groupService(GroupRepository groupRepository, MemberRepository memberRepository,
                                   PlaylistRepository playlistRepository, GroupMapper groupMapper,
                                   ApplicationEventPublisher eventPublisher) {
            return new GroupService(groupRepository, memberRepository, playlistRepository, groupMapper, eventPublisher);
        }

        @Bean
        GameSessionService gameSessionService(
                GameSessionRepository gameSessionRepository, PlayerRepository playerRepository,
                RoundRepository roundRepository, GuessRepository guessRepository, GroupRepository groupRepository,
                SongRepository songRepository, GroupService groupService, SessionMapper sessionMapper,
                SessionResultsStore resultsStore, GameSessionScheduler gameSessionScheduler,
                ApplicationEventPublisher eventPublisher) {
            return new GameSessionService(gameSessionRepository, playerRepository, roundRepository, guessRepository,
                    groupRepository, songRepository, groupService, sessionMapper, resultsStore,
                    gameSessionScheduler, eventPublisher);
        }

        @Bean
        GameSessionStartListener gameSessionStartListener(GameSessionService gameSessionService) {
            return new GameSessionStartListener(gameSessionService);
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

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
    private PlayerRepository playerRepository;
    @Autowired
    private RoundRepository roundRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setRole(Role.USER);
        user.setAuthProvider(AuthProvider.LOCAL);
        return userRepository.save(user);
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    @Test
    void exactlyOneOfSeveralSimultaneousBetsIsAcceptedAndTheOthersLoseNothing() throws InterruptedException {
        User admin = persistUser("betting-race-admin-" + System.nanoTime());
        User dj = persistUser("betting-race-dj-" + System.nanoTime());
        User bettorOne = persistUser("betting-race-bettor-one-" + System.nanoTime());
        User bettorTwo = persistUser("betting-race-bettor-two-" + System.nanoTime());

        authenticateAs(admin);
        GroupDetailDTO createdGroup = groupService.createGroup(new CreateGroupRequest(null, null));
        for (User member : List.of(dj, bettorOne, bettorTwo)) {
            authenticateAs(member);
            groupService.joinGroup(new JoinGroupRequest(createdGroup.inviteCode(), null, null, null));
        }

        Playlist playlist = new Playlist();
        playlist.setName("Betting Race Playlist");
        playlist.setColor("889900");
        playlist.setInviteCode("betting-race-playlist-" + System.nanoTime());
        Playlist savedPlaylist = playlistRepository.save(playlist);
        // Playlist.users is the mappedBy (inverse) side of this many-to-many; only
        // updating User.playlists, the owning side, actually writes the join table row.
        // Each service call in this non-transactional test opens its own persistence
        // context, so an inverse-side-only update here would never reach the database.
        admin.getPlaylists().add(savedPlaylist);
        userRepository.save(admin);
        for (int songIndex = 0; songIndex < 10; songIndex++) {
            Song song = new Song();
            song.setTitle("Betting Race Song " + songIndex);
            song.setReleaseYear(1970 + songIndex);
            song.setYoutubeId("betting-race-yt-" + songIndex);
            savedPlaylist.addSong(song);
            song.setAddedBy(admin);
            Song savedSong = songRepository.save(song);
            SongArtist artist = new SongArtist();
            artist.setSong(savedSong);
            artist.setName("Betting Race Artist " + songIndex);
            artist.setRole(ArtistRole.MAIN);
            artist.setDisplayOrder(0);
            savedSong.getArtists().add(artist);
            songRepository.save(savedSong);
        }
        // Playlist.songs is the owning side of its own many-to-many with Song; savedPlaylist
        // is detached in this non-transactional test, so addSong's in-memory mutation above
        // never reaches song_playlists without an explicit save here.
        playlistRepository.save(savedPlaylist);

        authenticateAs(admin);
        groupService.updateGroupSettings(createdGroup.id(), new UpdateGroupSettingsRequest(
                Set.of(savedPlaylist.getId()), DjMode.ROTATING, 5));
        groupService.startGameSession(createdGroup.id());

        GameSession session = gameSessionRepository.findByGroupId(createdGroup.id()).orElseThrow();
        Long sessionId = session.getId();
        Round round = roundRepository.findTopBySessionOrderByRoundNumberDesc(session).orElseThrow();

        List<Player> playersByTurnOrder = playerRepository.findBySessionOrderByTurnOrderAsc(session);
        Player bettorOnePlayer = playersByTurnOrder.get(2);
        Player bettorTwoPlayer = playersByTurnOrder.get(3);
        bettorOnePlayer.setTokenCount(1);
        bettorTwoPlayer.setTokenCount(1);
        playerRepository.save(bettorOnePlayer);
        playerRepository.save(bettorTwoPlayer);

        round.setStatus(RoundStatus.BETTING);
        round.setBettingWindowEndsAt(java.time.Instant.now().plusSeconds(30));
        roundRepository.save(round);

        Long bettorOneUserId = bettorOnePlayer.getUser().getId();
        Long bettorTwoUserId = bettorTwoPlayer.getUser().getId();
        int totalThreads = CONCURRENT_ATTEMPTS_PER_ELIGIBLE_BETTOR * 2;
        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);
        AtomicInteger acceptedCount = new AtomicInteger(0);

        for (int attempt = 0; attempt < CONCURRENT_ATTEMPTS_PER_ELIGIBLE_BETTOR; attempt++) {
            executorService.submit(() -> runBetAttempt(sessionId, bettorOneUserId, startLatch, doneLatch, acceptedCount));
            executorService.submit(() -> runBetAttempt(sessionId, bettorTwoUserId, startLatch, doneLatch, acceptedCount));
        }
        startLatch.countDown();
        boolean completedInTime = doneLatch.await(CONCURRENCY_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executorService.shutdownNow();

        assertThat(completedInTime).isTrue();
        assertThat(acceptedCount.get()).isEqualTo(1);

        Round finalRound = roundRepository.findById(round.getId()).orElseThrow();
        assertThat(finalRound.getBettorPlayer()).isNotNull();
        Player finalBettorOne = playerRepository.findById(bettorOnePlayer.getId()).orElseThrow();
        Player finalBettorTwo = playerRepository.findById(bettorTwoPlayer.getId()).orElseThrow();

        boolean bettorOneWonTheRace = finalRound.getBettorPlayer().getId().equals(bettorOnePlayer.getId());
        if (bettorOneWonTheRace) {
            assertThat(finalBettorOne.getTokenCount()).isZero();
            assertThat(finalBettorTwo.getTokenCount()).isEqualTo(1);
        } else {
            assertThat(finalBettorTwo.getTokenCount()).isZero();
            assertThat(finalBettorOne.getTokenCount()).isEqualTo(1);
        }
    }

    private void runBetAttempt(Long sessionId, Long userId, CountDownLatch startLatch, CountDownLatch doneLatch,
                                AtomicInteger acceptedCount) {
        try {
            startLatch.await();
            boolean accepted = gameSessionService.placeBet(sessionId, userId);
            if (accepted) {
                acceptedCount.incrementAndGet();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            doneLatch.countDown();
        }
    }
}
