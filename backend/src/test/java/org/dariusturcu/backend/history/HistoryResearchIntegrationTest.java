package org.dariusturcu.backend.history;

import org.dariusturcu.backend.analytics.AnalyticsRetentionService;
import org.dariusturcu.backend.difficulty.*;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.session.*;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.*;
import org.dariusturcu.backend.research.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class HistoryResearchIntegrationTest {
    private static final int RETENTION_DAYS = 365;
    private static final int PERFORMANCE_CATALOG_SIZE = 100_000;
    private static final int GENERATED_POOL_SIZE = 96;
    private static final int PERFORMANCE_SAMPLE_COUNT = 20;
    private static final int WIN_TARGET = 8;
    private static final int ANCHOR_YEAR = 1990;
    @Container
    static final PostgreSQLContainer<?> coreDatabase = new PostgreSQLContainer<>("pgvector/pgvector:pg18");
    @Container
    static final PostgreSQLContainer<?> analyticsDatabase = new PostgreSQLContainer<>("postgres:18");
    private static JdbcTemplate core;
    private static JdbcTemplate analytics;
    private static TransactionTemplate coreTransaction;
    private RoundRepository rounds;
    private GroupRepository groups;
    private SongRepository songs;
    private GameHistoryService history;
    private SongResearchService research;
    private ResearchDeliveryService delivery;
    private PreparedDifficultyService difficulty;
    private User firstUser;
    private User secondUser;
    private GameSession session;
    private Round turn;
    private JsonMapper mapper;

    @BeforeAll
    static void migrate() {
        var coreSource = new DriverManagerDataSource(coreDatabase.getJdbcUrl(), coreDatabase.getUsername(), coreDatabase.getPassword());
        var analyticsSource = new DriverManagerDataSource(analyticsDatabase.getJdbcUrl(), analyticsDatabase.getUsername(), analyticsDatabase.getPassword());
        Flyway.configure().dataSource(coreSource).locations("classpath:db/migration").load().migrate();
        Flyway.configure().dataSource(analyticsSource).locations("classpath:db/analytics-migration").load().migrate();
        core = new JdbcTemplate(coreSource);
        analytics = new JdbcTemplate(analyticsSource);
        coreTransaction = new TransactionTemplate(new JdbcTransactionManager(coreSource));
    }

    @BeforeEach
    void fixture() {
        core.execute("TRUNCATE game_summaries, research_delivery_queue, research_player_identities, songs, users CASCADE");
        analytics.execute("TRUNCATE song_play_observations, song_difficulty_aggregates, research_processed_events, research_deleted_players");
        rounds = mock(RoundRepository.class);
        groups = mock(GroupRepository.class);
        songs = mock(SongRepository.class);
        mapper = JsonMapper.builder().findAndAddModules().build();
        history = new GameHistoryService(core, rounds, groups);
        research = new SongResearchService(core, analytics, mapper);
        delivery = new ResearchDeliveryService(core, analytics, mapper);
        difficulty = new PreparedDifficultyService(core, analytics, songs, new SongDifficultyScorer(), new DifficultyBand());
        firstUser = user("First");
        secondUser = user("Second");
        session = new GameSession();
        session.setId(91L);
        session.setGroupId(17L);
        session.setCreatedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        session.setWinConditionCardCount(WIN_TARGET);
        session.setDifficultyTier(DifficultyTier.MEDIUM);
        Player firstPlayer = player(firstUser, 31L);
        Player secondPlayer = player(secondUser, 32L);
        session.addPlayer(firstPlayer);
        session.addPlayer(secondPlayer);
        Song song = song(20);
        turn = new Round();
        turn.setId(45L);
        turn.setSession(session);
        turn.setSong(song);
        turn.setActivePlayer(firstPlayer);
        turn.setDjPlayer(secondPlayer);
        turn.setStatus(RoundStatus.SCORED);
        turn.setScoredAt(Instant.now());
        turn.setPlacedPosition(1);
        turn.setPlacementCorrect(true);
        turn.setTimelineCardCount(3);
        turn.setValidInsertionSlotCount(1);
        Group group = new Group();
        group.setJoinCode("ABCD");
        when(groups.findById(session.getGroupId())).thenReturn(Optional.of(group));
        when(rounds.findBySessionOrderByRoundNumberAsc(session)).thenReturn(List.of(turn));
    }

    private User user(String name) {
        User user = new User();
        user.setId(core.queryForObject("INSERT INTO users(username) VALUES (?) RETURNING id", Long.class, name));
        user.setUsername(name);
        return user;
    }

    private Player player(User user, Long playerId) {
        Player player = new Player();
        player.setId(playerId);
        player.setUser(user);
        player.setDisplayName(user.getUsername());
        player.setStatus(PlayerStatus.ACTIVE);
        Song anchor = new Song();
        anchor.setReleaseYear(ANCHOR_YEAR);
        player.addCard(PlayerCard.of(anchor));
        return player;
    }

    private Song song(Integer sitelinks) {
        Song song = new Song();
        song.setId(core.queryForObject("INSERT INTO songs(title, release_year, verification_status, wikidata_sitelinks_count) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, "Song", 2000, "VERIFIED", sitelinks));
        song.setWikidataSitelinksCount(sitelinks);
        return song;
    }

    private SessionResultsDTO results() {
        return new SessionResultsDTO(session.getGroupId(), session.getPlayers().stream()
                .map(player -> new PlayerResultDTO(player.getId(), player.getDisplayName(), player.getTimeline().size(), 1)).toList(),
                session.getPlayers().stream().map(player -> new LeaderboardEntryDTO(player.getId(), player.getDisplayName(), player.getTotalArtistsGuessed(), 1)).toList(),
                session.getPlayers().stream().map(player -> new LeaderboardEntryDTO(player.getId(), player.getDisplayName(), player.getTotalTitlesGuessed(), 1)).toList());
    }

    private void complete(String reason) {
        coreTransaction.executeWithoutResult(status -> history.completed(new GameCompleted(session, results(), reason)));
    }

    @Test
    void summariesPreserveTiesAndSurviveGroupDisappearance() {
        session.getPlayers().getLast().setStatus(PlayerStatus.LEFT);
        complete(GameHistoryService.TARGET_REACHED);
        reset(groups);
        GameHistoryPage page = history.list(firstUser.getId(), 0, 20);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst().groupName()).isEqualTo("Group ABCD");
        assertThat(page.items().getFirst().participants()).allMatch(GameParticipantSummary::isWinner);
        assertThat(page.items().getFirst().participants()).extracting(GameParticipantSummary::participationStatus).contains("LEFT");
        assertThat(history.statistics(firstUser.getId()).wins()).isEqualTo(1);
    }

    @Test
    void interruptedGamesRemainVisibleButDoNotCountAsWins() {
        complete(GameHistoryService.INTERRUPTED);
        assertThat(history.statistics(firstUser.getId()).gamesPlayed()).isZero();
        assertThat(history.statistics(firstUser.getId()).wins()).isZero();
        assertThat(history.statistics(firstUser.getId()).interruptedGames()).isEqualTo(1);
        assertThat(history.list(firstUser.getId(), 0, 20).items().getFirst().participants()).noneMatch(GameParticipantSummary::isWinner);
    }

    @Test
    void outsiderCannotReadParticipantsHistory() {
        complete(GameHistoryService.TARGET_REACHED);
        User outsider = user("Later joiner");
        long summaryId = history.list(firstUser.getId(), 0, 20).items().getFirst().id();
        assertThat(history.list(outsider.getId(), 0, 20).total()).isZero();
        assertThatThrownBy(() -> history.detail(outsider.getId(), summaryId)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void completionIsIdempotentAndLaterGamesDoNotOverwriteEarlierGames() {
        complete(GameHistoryService.TARGET_REACHED);
        complete(GameHistoryService.TARGET_REACHED);
        session.setId(92L);
        complete(GameHistoryService.SONGS_EXHAUSTED);
        assertThat(history.export(firstUser.getId())).hasSize(2);
    }

    @Test
    void deletingAccountsAnonymizesSharedHistoryThenRemovesUnownedHistory() {
        complete(GameHistoryService.TARGET_REACHED);
        history.deleted(new AccountHistoryDeleted(firstUser.getId()));
        assertThat(history.list(firstUser.getId(), 0, 20).total()).isZero();
        assertThat(history.export(secondUser.getId()).getFirst().participants())
                .anyMatch(participant -> participant.userId() == null && participant.displayName().equals("Deleted player"));
        history.deleted(new AccountHistoryDeleted(secondUser.getId()));
        assertThat(core.queryForObject("SELECT count(*) FROM game_summaries", Integer.class)).isZero();
    }

    @Test
    void summaryWritesRollBackWithTheCoreTransaction() {
        assertThatThrownBy(() -> coreTransaction.executeWithoutResult(status -> {
            history.completed(new GameCompleted(session, results(), GameHistoryService.TARGET_REACHED));
            throw new IllegalStateException("Transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(history.list(firstUser.getId(), 0, 20).total()).isZero();
    }

    @Test
    void invalidPaginationIsRejected() {
        assertThatThrownBy(() -> history.list(firstUser.getId(), -1, 20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> history.list(firstUser.getId(), 0, 101)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deliveryRetriesDoNotDoubleCountAndPersonalExportIncludesPendingObservations() {
        research.scored(new ScoredTurn(turn));
        assertThat(research.export(firstUser.getId())).hasSize(1);
        String payload = core.queryForObject("SELECT payload::text FROM research_delivery_queue", String.class);
        UUID eventId = core.queryForObject("SELECT event_id FROM research_delivery_queue", UUID.class);
        coreTransaction.executeWithoutResult(status -> delivery.deliverBatch());
        core.update("INSERT INTO research_delivery_queue(event_id, event_type, payload) VALUES (?, ?, CAST(? AS jsonb))",
                eventId, SongResearchService.OBSERVATION, payload);
        coreTransaction.executeWithoutResult(status -> delivery.deliverBatch());
        assertThat(analytics.queryForObject("SELECT sum(placement_attempts) FROM song_difficulty_aggregates", Long.class)).isEqualTo(1);
        assertThat(research.export(firstUser.getId())).hasSize(1);
    }

    @Test
    void skippedPlacementDoesNotBecomeAnIncorrectAttempt() {
        turn.setPlacedPosition(null);
        turn.setPlacementCorrect(false);
        research.scored(new ScoredTurn(turn));
        coreTransaction.executeWithoutResult(status -> delivery.deliverBatch());
        assertThat(analytics.queryForObject("SELECT sum(placement_attempts) FROM song_difficulty_aggregates", Long.class)).isZero();
    }

    @Test
    void deletionPreventsLateObservationsFromRestoringResearchIdentity() {
        research.scored(new ScoredTurn(turn));
        String payload = core.queryForObject("SELECT payload::text FROM research_delivery_queue", String.class);
        core.execute("DELETE FROM research_delivery_queue");
        research.deleted(new AccountHistoryDeleted(firstUser.getId()));
        coreTransaction.executeWithoutResult(status -> delivery.deliverBatch());
        SongPlayObservation observation = mapper.readValue(payload, SongPlayObservation.class);
        core.update("INSERT INTO research_delivery_queue(event_id, event_type, payload) VALUES (?, ?, CAST(? AS jsonb))",
                observation.eventId(), SongResearchService.OBSERVATION, payload);
        coreTransaction.executeWithoutResult(status -> delivery.deliverBatch());
        assertThat(analytics.queryForObject("SELECT research_player_id FROM song_play_observations", UUID.class)).isNull();
        assertThat(research.export(firstUser.getId())).isEmpty();
    }

    @Test
    void analyticsFailureLeavesTheCommittedQueueAvailableForRetry() {
        research.scored(new ScoredTurn(turn));
        JdbcTemplate unavailable = mock(JdbcTemplate.class);
        when(unavailable.getDataSource()).thenReturn(analytics.getDataSource());
        when(unavailable.update(anyString(), any(Object[].class))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Unavailable"));
        ResearchDeliveryService failingDelivery = new ResearchDeliveryService(core, unavailable, mapper);
        assertThatThrownBy(() -> coreTransaction.executeWithoutResult(status -> failingDelivery.deliverBatch()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(core.queryForObject("SELECT count(*) FROM research_delivery_queue", Integer.class)).isEqualTo(1);
        coreTransaction.executeWithoutResult(status -> delivery.deliverBatch());
        assertThat(core.queryForObject("SELECT count(*) FROM research_delivery_queue", Integer.class)).isZero();
    }

    @Test
    void oneYearExpiryPreservesAnonymousAggregates() {
        research.scored(new ScoredTurn(turn));
        coreTransaction.executeWithoutResult(status -> delivery.deliverBatch());
        analytics.update("UPDATE song_play_observations SET occurred_at = ?", Timestamp.from(Instant.now().minus(RETENTION_DAYS + 1, ChronoUnit.DAYS)));
        new AnalyticsRetentionService(analytics, RETENTION_DAYS).purgeExpiredEvents();
        assertThat(analytics.queryForObject("SELECT count(*) FROM song_play_observations", Integer.class)).isZero();
        assertThat(analytics.queryForObject("SELECT sum(placement_attempts) FROM song_difficulty_aggregates", Long.class)).isEqualTo(1);
    }

    @Test
    void preparedScoresAreAvailableOnCatalogInsertAndUpgrade() {
        assertThat(core.queryForObject("SELECT tier FROM song_difficulty WHERE song_id = ?", String.class, turn.getSong().getId())).isEqualTo("MEDIUM");
        Song obscure = song(null);
        assertThat(core.queryForObject("SELECT score FROM song_difficulty WHERE song_id = ?", Double.class, obscure.getId())).isEqualTo(0.5);
        core.update("UPDATE songs SET wikidata_sitelinks_count = ? WHERE id = ?", 40, obscure.getId());
        assertThat(core.queryForObject("SELECT tier FROM song_difficulty WHERE song_id = ?", String.class, obscure.getId())).isEqualTo("EASY");
    }

    @Test
    void researchScoresPublishIntoCoreAndDoNotNeedTemporaryRounds() {
        research.scored(new ScoredTurn(turn));
        coreTransaction.executeWithoutResult(status -> delivery.deliverBatch());
        when(songs.findByVerificationStatus(any())).thenReturn(List.of(turn.getSong()));
        difficulty.refresh();
        assertThat(core.queryForObject("SELECT placement_sample_count FROM song_difficulty WHERE song_id = ?", Long.class, turn.getSong().getId())).isEqualTo(1);
        verifyNoInteractions(rounds);
    }

    @Test
    void insufficientEligibleCatalogFailsExplicitly() {
        assertThatThrownBy(() -> difficulty.select(DifficultyTier.EASY, GENERATED_POOL_SIZE)).isInstanceOf(org.dariusturcu.backend.exception.ConflictException.class);
    }

    @Test
    void indexedSamplingVariesAndExcludesUnverifiedOrOutOfScopeSongsOnLargeCatalog() {
        core.update("INSERT INTO songs(title, release_year, verification_status, wikidata_sitelinks_count) SELECT 'Song ' || song_number, 2000, 'VERIFIED', 40 FROM generate_series(1, ?) song_number", PERFORMANCE_CATALOG_SIZE);
        core.execute("ANALYZE songs");
        core.execute("ANALYZE song_difficulty");
        when(songs.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            List<Song> selected = new ArrayList<>();
            for (Long songId : ids) { Song song = new Song(); song.setId(songId); selected.add(song); }
            return selected;
        });
        Set<List<Long>> generated = new HashSet<>();
        long started = System.nanoTime();
        for (int sampleNumber = 0; sampleNumber < PERFORMANCE_SAMPLE_COUNT; sampleNumber++) {
            List<Long> pool = difficulty.select(DifficultyTier.EASY, GENERATED_POOL_SIZE).stream().map(Song::getId).toList();
            assertThat(pool).hasSize(GENERATED_POOL_SIZE).doesNotHaveDuplicates();
            assertThat(pool).doesNotContain(turn.getSong().getId());
            generated.add(pool);
        }
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        System.out.println("Prepared selection: catalog=" + PERFORMANCE_CATALOG_SIZE + ", samples=" + PERFORMANCE_SAMPLE_COUNT
                + ", pool=" + GENERATED_POOL_SIZE + ", meanMillis=" + elapsedMillis / PERFORMANCE_SAMPLE_COUNT);
        assertThat(generated).hasSize(PERFORMANCE_SAMPLE_COUNT);
        verify(songs, never()).findByVerificationStatus(any());
        verifyNoInteractions(rounds);
    }

    @Test
    void historyEndpointsUseTheAuthenticatedAccountAndRejectOutsiders() throws Exception {
        complete(GameHistoryService.TARGET_REACHED);
        org.springframework.test.web.servlet.MockMvc mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new GameHistoryController(history))
                .setControllerAdvice(new org.dariusturcu.backend.exception.GlobalExceptionHandler()).build();
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        try {
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    new org.dariusturcu.backend.security.UserPrincipal(firstUser), null, List.of()));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/me/history"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(1));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/me/statistics"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.wins").value(1));
            long summaryId = history.export(firstUser.getId()).getFirst().id();
            User outsider = user("Outsider");
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    new org.dariusturcu.backend.security.UserPrincipal(outsider), null, List.of()));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/me/history/" + summaryId))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/me/history"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void accountExportIncludesCoreHistoryAndPendingResearchForOnlyItsOwner() {
        complete(GameHistoryService.TARGET_REACHED);
        research.scored(new ScoredTurn(turn));
        UserRepository users = mock(UserRepository.class);
        PlaylistMembershipRepository memberships = mock(PlaylistMembershipRepository.class);
        when(users.findById(firstUser.getId())).thenReturn(Optional.of(firstUser));
        org.dariusturcu.backend.service.PersonalDataExportService exportService = new org.dariusturcu.backend.service.PersonalDataExportService(
                users, memberships, songs, mock(org.dariusturcu.backend.model.mapper.SongMapper.class), history, research);
        try {
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            new org.dariusturcu.backend.security.UserPrincipal(firstUser), null, List.of()));
            var exported = exportService.exportCurrentUser();
            assertThat(exported.gameHistory()).hasSize(1);
            assertThat(exported.researchObservations()).hasSize(1);
            assertThat(research.export(secondUser.getId())).isEmpty();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void analyticsFailurePreservesPreparedScores() {
        JdbcTemplate unavailable = mock(JdbcTemplate.class);
        when(unavailable.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Unavailable"));
        PreparedDifficultyService unavailableRefresh = new PreparedDifficultyService(core, unavailable, songs, new SongDifficultyScorer(), new DifficultyBand());
        Double priorScore = core.queryForObject("SELECT score FROM song_difficulty WHERE song_id = ?", Double.class, turn.getSong().getId());
        assertThatThrownBy(unavailableRefresh::refresh).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(core.queryForObject("SELECT score FROM song_difficulty WHERE song_id = ?", Double.class, turn.getSong().getId())).isEqualTo(priorScore);
    }
    @Test
    void pendingRawObservationsExpireWithoutAnalyticsAndDeletionMessagesRemain() {
        research.scored(new ScoredTurn(turn));
        core.update("UPDATE research_delivery_queue SET payload = jsonb_set(payload, '{occurredAt}', to_jsonb(?::text))",
                Instant.now().minus(RETENTION_DAYS + 1, ChronoUnit.DAYS).toString());
        research.deleted(new AccountHistoryDeleted(firstUser.getId()));
        assertThat(delivery.purgeExpiredQueuedObservations()).isEqualTo(1);
        assertThat(core.queryForObject("SELECT event_type FROM research_delivery_queue", String.class))
                .isEqualTo(SongResearchService.DELETE_PLAYER);
    }
}
