package org.dariusturcu.backend.session;

import org.dariusturcu.backend.model.group.CreateGroupRequest;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.GroupStatus;
import org.dariusturcu.backend.model.group.JoinGroupRequest;
import org.dariusturcu.backend.model.group.UpdateGroupSettingsRequest;
import org.dariusturcu.backend.model.mapper.GroupMapper;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SessionMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.PlaceCardRequest;
import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.session.PlayerStatus;
import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.model.session.SessionResultsDTO;
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
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the full game-session lifecycle against a real Postgres instance: session
 * init off a group's settings, roster snapshot, round-by-round play through win
 * condition, results export, and the purge that hands control back to the group. Also
 * covers auto-abandon and the disconnect/turn-timeout/no-restore-on-reconnect sequence.
 *
 * The TaskScheduler bean in this context fires every scheduled effect immediately rather
 * than waiting out its real delay (see ImmediateTaskScheduler below): this is the "test
 * TaskScheduler that fires immediately" case the batch's timer design calls for, proving
 * the real scheduling wiring works without a multi-second real wait per round. The betting
 * concurrency test lives in a separate, non-transactional test class instead, since it
 * needs a round's BETTING state actually committed and visible to other real connections
 * before racing concurrent bets against it.
 */
@Testcontainers
@SpringBootTest(classes = GameSessionLifecycleIntegrationTest.JpaTestConfig.class)
@Transactional
class GameSessionLifecycleIntegrationTest {

    private static final int MAX_ROUNDS_BEFORE_GIVING_UP = 30;

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
        SessionMapper sessionMapper() {
            return new SessionMapper();
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
    private GroupRepository groupRepository;
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
    @Autowired
    private SessionResultsStore resultsStore;

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

    // Strictly increasing release years across every song this test adds, so that
    // placing each round's song at the end of whichever player's timeline is playing it
    // is always the correct placement: every year handed out so far is guaranteed smaller
    // than every year still queued.
    private Song persistSong(Playlist playlist, User addedBy, int releaseYear, String title) {
        Song song = new Song();
        song.setTitle(title);
        song.setReleaseYear(releaseYear);
        song.setYoutubeId("yt-" + releaseYear);
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

    @Test
    void fullSessionLifecycleFromStartThroughWinConditionToPurgedState() {
        User admin = persistUser("session-lifecycle-admin");
        User playerTwo = persistUser("session-lifecycle-player-two");

        authenticateAs(admin);
        GroupDetailDTO createdGroup = groupService.createGroup(new CreateGroupRequest(null, null));
        authenticateAs(playerTwo);
        groupService.joinGroup(new JoinGroupRequest(createdGroup.inviteCode(), null, null, null));

        Playlist playlist = new Playlist();
        playlist.setName("Lifecycle Playlist");
        playlist.setColor("112233");
        playlist.setInviteCode("lifecycle-playlist-" + System.nanoTime());
        playlist.setOwner(admin);
        Playlist savedPlaylist = playlistRepository.save(playlist);
        for (int songIndex = 0; songIndex < 15; songIndex++) {
            persistSong(savedPlaylist, admin, 1950 + songIndex, "Song " + songIndex);
        }

        authenticateAs(admin);
        int minimumWinConditionCardCount = 5;
        groupService.updateGroupSettings(createdGroup.id(), new UpdateGroupSettingsRequest(
                Set.of(savedPlaylist.getId()), DjMode.ROTATING, minimumWinConditionCardCount));

        groupService.startGameSession(createdGroup.id());

        GameSession session = gameSessionRepository.findByGroupId(createdGroup.id()).orElseThrow();
        assertThat(session.getPlayers()).hasSize(2);
        assertThat(session.getPlayers()).allSatisfy(player -> assertThat(player.getTimeline()).hasSize(1));

        Long groupId = createdGroup.id();
        Long sessionId = session.getId();
        int roundsPlayed = 0;
        while (gameSessionRepository.findById(sessionId).isPresent() && roundsPlayed < MAX_ROUNDS_BEFORE_GIVING_UP) {
            GameSession currentSession = gameSessionRepository.findById(sessionId).orElseThrow();
            Round currentRound = roundRepository.findTopBySessionOrderByRoundNumberDesc(currentSession).orElseThrow();
            Player activePlayer = currentRound.getActivePlayer();
            int correctPosition = activePlayer.getTimeline().size();
            gameSessionService.lockInPlacement(sessionId, activePlayer.getUser().getId(), new PlaceCardRequest(correctPosition));
            roundsPlayed++;
        }

        assertThat(gameSessionRepository.findById(sessionId)).isEmpty();
        assertThat(playerRepository.findAll()).isEmpty();
        assertThat(roundRepository.findAll()).isEmpty();

        Optional<SessionResultsDTO> results = resultsStore.get(groupId);
        assertThat(results).isPresent();
        assertThat(results.get().cardCountRanking()).hasSize(2);
        assertThat(results.get().cardCountRanking().get(0).cardCount()).isEqualTo(minimumWinConditionCardCount);
        assertThat(results.get().cardCountRanking().get(0).rank()).isEqualTo(1);
        assertThat(results.get().mostArtistsGuessed()).hasSize(2);
        assertThat(results.get().mostTitlesGuessed()).hasSize(2);

        Group groupAfter = groupRepository.findById(groupId).orElseThrow();
        assertThat(groupAfter.getStatus()).isEqualTo(GroupStatus.OPEN);
        assertThat(groupAfter.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void bettingWindowIsSkippedEntirelyWhenNoPlayerHoldsAToken() {
        GameSession session = startTwoPlayerRotatingSession("betting-skip");
        Round initialRound = firstRound(session);
        Long initialRoundId = initialRound.getId();
        Player activePlayer = initialRound.getActivePlayer();

        gameSessionService.lockInPlacement(session.getId(), activePlayer.getUser().getId(),
                new PlaceCardRequest(activePlayer.getTimeline().size()));

        Round scoredRound = roundRepository.findById(initialRoundId).orElseThrow();
        assertThat(scoredRound.getStatus()).isEqualTo(RoundStatus.SCORED);
        assertThat(scoredRound.getBettorPlayer()).isNull();
    }

    @Test
    void autoAbandonPurgesTheSessionWithNoResultsWhenEveryPlayerDisconnects() {
        // Three players, and the round's active player disconnects last: under this
        // test's immediate-fire scheduler, disconnecting the active player also triggers
        // an immediate turn timeout, which reassigns the active-player role among the
        // two players still connected rather than ending the session outright. Only once
        // every player has actually disconnected does the auto-abandon check fire.
        GameSession session = startRotatingSession("auto-abandon", 3);
        Long groupId = session.getGroupId();
        Long sessionId = session.getId();
        Long activeUserId = firstRound(session).getActivePlayer().getUser().getId();

        java.util.List<Long> playerUserIds = session.getPlayers().stream()
                .map(player -> player.getUser().getId())
                .sorted(java.util.Comparator.comparing(userId -> userId.equals(activeUserId)))
                .toList();
        for (Long userId : playerUserIds) {
            // The immediate-fire scheduler runs the auto-abandon check the instant the
            // last connected player drops, rather than after a real 10-minute wait.
            gameSessionService.disconnectPlayer(sessionId, userId);
        }

        assertThat(gameSessionRepository.findById(sessionId)).isEmpty();
        assertThat(resultsStore.get(groupId)).isEmpty();
        assertThat(groupRepository.findById(groupId).orElseThrow().getStatus()).isEqualTo(GroupStatus.OPEN);
    }

    @Test
    void aDisconnectedActivePlayerIsAutoSkippedAndMarkedLeftAndAReconnectDoesNotRestoreThem() {
        // Three players, not two: after the active player is marked Left, at least two
        // players must remain for the round to advance rather than ending the session.
        GameSession session = startRotatingSession("disconnect-timeout", 3);
        Player activePlayer = firstRound(session).getActivePlayer();
        Long sessionId = session.getId();
        Long activeUserId = activePlayer.getUser().getId();

        // The immediate-fire scheduler runs the 90-second turn-timeout effect the instant
        // the active player disconnects, rather than after a real 90-second wait.
        gameSessionService.disconnectPlayer(sessionId, activeUserId);

        Player afterTimeout = playerRepository.findById(activePlayer.getId()).orElseThrow();
        assertThat(afterTimeout.getStatus()).isEqualTo(PlayerStatus.LEFT);

        gameSessionService.reconnectPlayer(sessionId, activeUserId);
        Player afterReconnect = playerRepository.findById(activePlayer.getId()).orElseThrow();
        assertThat(afterReconnect.isConnected()).isTrue();
        assertThat(afterReconnect.getStatus()).isEqualTo(PlayerStatus.LEFT);
    }

    private Round firstRound(GameSession session) {
        return roundRepository.findTopBySessionOrderByRoundNumberDesc(session).orElseThrow();
    }

    private GameSession startTwoPlayerRotatingSession(String label) {
        return startRotatingSession(label, 2);
    }

    private GameSession startRotatingSession(String label, int playerCount) {
        User admin = persistUser(label + "-admin-" + System.nanoTime());

        authenticateAs(admin);
        GroupDetailDTO createdGroup = groupService.createGroup(new CreateGroupRequest(null, null));

        for (int playerIndex = 1; playerIndex < playerCount; playerIndex++) {
            User otherPlayer = persistUser(label + "-player-" + playerIndex + "-" + System.nanoTime());
            authenticateAs(otherPlayer);
            groupService.joinGroup(new JoinGroupRequest(createdGroup.inviteCode(), null, null, null));
        }

        Playlist playlist = new Playlist();
        playlist.setName(label + " Playlist");
        playlist.setColor("445566");
        playlist.setInviteCode(label + "-playlist-" + System.nanoTime());
        playlist.setOwner(admin);
        Playlist savedPlaylist = playlistRepository.save(playlist);
        for (int songIndex = 0; songIndex < 10; songIndex++) {
            persistSong(savedPlaylist, admin, 1960 + songIndex, label + " Song " + songIndex);
        }

        authenticateAs(admin);
        groupService.updateGroupSettings(createdGroup.id(), new UpdateGroupSettingsRequest(
                Set.of(savedPlaylist.getId()), DjMode.ROTATING, 5));
        groupService.startGameSession(createdGroup.id());

        return gameSessionRepository.findByGroupId(createdGroup.id()).orElseThrow();
    }
}
