package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.song.CatalogSongStatus;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.RecheckCandidateDTO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the full migration history, then the catalog list and count queries against songs and
 * backlog rows covering every status, pipeline, and added-by case.
 */
@Testcontainers
class AdminSongCatalogRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    private static final int WHOLE_CATALOG = 25;
    private static final int SMALL_PAGE = 4;
    private static final long FIRST_OFFSET = 0;
    private static final long THIRD_PAGE_OFFSET = 8;
    private static final long CATALOG_ROW_COUNT = 9;
    private static final long WAITING_STAGE_JOBS = 4;
    private static final long WAITING_EMBEDDINGS = 2;
    private static final long WAITING_QUEUE_ROWS = WAITING_STAGE_JOBS + WAITING_EMBEDDINGS;
    private static final long IN_PROGRESS_QUEUE_ROWS = 1;

    private static final String BARBIE_GIRL = "Barbie Girl";
    private static final String QUEUED_ONLY_ID = "queued-only-id";
    private static final String FAILED_ONLY_ID = "failed-only-id";
    private static final String REQUEUED_ID = "requeued-id";
    private static final String ZI_MI = "Zi-mi";
    private static final String ZI_MI_YOUTUBE_ID = "zimi-yt-id";
    private static final String DRAGOSTEA = "Dragostea Din Tei";
    private static final String SA_NU_MI = "Sa nu-mi iei niciodata";
    private static final String TAKE_FIVE = "Take Five";
    private static final String UNKNOWN_ORIGIN = "Unknown Origin";

    private static JdbcTemplate jdbcTemplate;
    private static AdminSongCatalogRepository repository;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        repository = new AdminSongCatalogRepository(jdbcTemplate);
    }

    @BeforeEach
    void seedCatalog() {
        jdbcTemplate.execute("TRUNCATE game_session_song_queue, game_sessions, groups RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE pipeline_runs, song_embedding_queue, songs, song_artists, pending_imports, users RESTART IDENTITY CASCADE");
        long maraId = insertUser("mara.k");
        long lenaId = insertUser("lena88");

        long dragosteaId = insertSong(DRAGOSTEA, 2003, "dragostea-id", "VERIFIED", null, "2026-10-14T12:00:00Z");
        insertArtist(dragosteaId, "O-Zone");
        insertQueueRow("dragostea-id", PendingImportOrigin.ADMIN_SEED, "DONE", "2026-10-14T11:00:00");

        long ziMiId = insertSong(ZI_MI, 2019, ZI_MI_YOUTUBE_ID, "NEEDS_REVIEW", maraId, "2026-10-15T09:14:00Z");
        insertArtist(ziMiId, "Loredana");
        insertQueueRow(ZI_MI_YOUTUBE_ID, PendingImportOrigin.FAST_TIER_RECHECK, "FAILED", "2026-10-15T09:14:00");

        long barbieId = insertSong(BARBIE_GIRL, 1997, "barbie-id", "UNVERIFIED", null, "2026-10-16T10:00:00Z");
        insertArtist(barbieId, "Aqua");
        insertQueueRow("barbie-id", PendingImportOrigin.ADMIN_SEED, "PROCESSING", "2026-10-16T09:00:00");

        long takeFiveId = insertSong(TAKE_FIVE, 1959, "take-five-id", "VERIFIED", lenaId, "2026-10-11T08:00:00Z");
        insertArtist(takeFiveId, "The Dave Brubeck Quartet");
        insertQueueRow("take-five-id", PendingImportOrigin.USER_ADD_RECHECK, "DONE", "2026-10-11T08:00:00");

        insertSong(UNKNOWN_ORIGIN, 0, "unknown-origin-id", "MANUAL_ENTRY", null, null);

        long saNuMiId = insertSong(SA_NU_MI, 1996, "sa-nu-mi-id", "UNVERIFIED", lenaId, "2026-10-13T08:00:00Z");
        insertArtist(saNuMiId, "Holograf");
        insertQueueRow("sa-nu-mi-id", PendingImportOrigin.USER_ADD_RECHECK, "PENDING", "2026-10-13T08:00:00");

        insertQueueRow(QUEUED_ONLY_ID, PendingImportOrigin.ADMIN_SEED, "PENDING", "2026-10-16T08:00:00");
        insertQueueRow(FAILED_ONLY_ID, PendingImportOrigin.ADMIN_SEED, "FAILED", "2026-10-16T05:00:00");
        insertQueueRow(FAILED_ONLY_ID, PendingImportOrigin.ADMIN_SEED, "FAILED", "2026-10-16T06:00:00");
        insertQueueRow(REQUEUED_ID, PendingImportOrigin.ADMIN_SEED, "FAILED", "2026-10-09T07:00:00");
        insertQueueRow(REQUEUED_ID, PendingImportOrigin.ADMIN_SEED, "PENDING", "2026-10-16T07:30:00");
        insertQueueRow("orphan-recheck-id", PendingImportOrigin.FAST_TIER_RECHECK, "PENDING", "2026-10-16T09:30:00");
        insertQueueRow("done-without-song-id", PendingImportOrigin.ADMIN_SEED, "DONE", "2026-10-16T09:45:00");
    }

    private static long insertUser(String username) {
        return jdbcTemplate.queryForObject("INSERT INTO users (username) VALUES (?) RETURNING id", Long.class, username);
    }

    private static long insertSong(String title, int releaseYear, String youtubeId, String verificationStatus,
                                   Long addedBy, String createdAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO songs (title, release_year, youtube_id, verification_status, added_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, title, releaseYear, youtubeId, verificationStatus, addedBy,
                createdAt == null ? null : OffsetDateTime.ofInstant(Instant.parse(createdAt), ZoneOffset.UTC));
    }

    private static void insertArtist(long songId, String name) {
        jdbcTemplate.update("INSERT INTO song_artists (song_id, name, role, display_order) VALUES (?, ?, 'MAIN', 0)",
                songId, name);
    }

    private static void insertQueueRow(String youtubeId, PendingImportOrigin origin, String status, String enqueuedAt) {
        jdbcTemplate.update("INSERT INTO pending_imports (youtube_id, origin, status, enqueued_at) VALUES (?, ?, ?, ?)",
                youtubeId, origin.name(), status, Timestamp.valueOf(LocalDateTime.parse(enqueuedAt)));
        String runStatus = switch (status) {
            case "DONE", "FAILED" -> status;
            default -> "ACTIVE";
        };
        String jobStatus = switch (status) {
            case "PENDING" -> "WAITING";
            case "PROCESSING" -> "IN_PROGRESS";
            default -> status;
        };
        Long runId = jdbcTemplate.queryForObject("""
                INSERT INTO pipeline_runs (youtube_id, origin, lane, status, created_at)
                VALUES (?, ?, 'BACKFILL', ?, ?) RETURNING id
                """, Long.class, youtubeId, origin.name(), runStatus,
                OffsetDateTime.of(LocalDateTime.parse(enqueuedAt), ZoneOffset.UTC));
        jdbcTemplate.update("""
                INSERT INTO pipeline_jobs (run_id, stage, lane, status, max_attempts)
                VALUES (?, 'YOUTUBE_METADATA', 'BACKFILL', ?, ?)
                """, runId, jobStatus, org.dariusturcu.backend.pipeline.PipelineJobService.DEFAULT_MAX_ATTEMPTS);
    }

    private static List<String> titlesOrIds(List<AdminCatalogRow> rows) {
        return rows.stream().map(row -> row.title() == null ? row.youtubeId() : row.title()).toList();
    }

    private static AdminCatalogRow rowFor(List<AdminCatalogRow> rows, String titleOrId) {
        return rows.stream()
                .filter(row -> titleOrId.equals(row.title()) || titleOrId.equals(row.youtubeId()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theCatalogListsSongsAndUnresolvedBacklogItemsNewestFirstWithUnknownDatesLast() {
        List<AdminCatalogRow> rows = repository.findPage(null, null, false, WHOLE_CATALOG, FIRST_OFFSET);

        assertThat(titlesOrIds(rows)).containsExactly(BARBIE_GIRL, QUEUED_ONLY_ID, FAILED_ONLY_ID, ZI_MI, DRAGOSTEA,
                SA_NU_MI, TAKE_FIVE, REQUEUED_ID, UNKNOWN_ORIGIN);
    }

    @Test
    void eachRowCarriesItsStatusAndPipelineFacts() {
        List<AdminCatalogRow> rows = repository.findPage(null, null, false, WHOLE_CATALOG, FIRST_OFFSET);

        AdminCatalogRow dragostea = rowFor(rows, DRAGOSTEA);
        assertThat(dragostea.status()).isEqualTo(CatalogSongStatus.VERIFIED);
        assertThat(dragostea.latestQueueOrigin()).isEqualTo(PendingImportOrigin.ADMIN_SEED);
        assertThat(dragostea.hasPipelineRun()).isTrue();
        assertThat(dragostea.hasAdminSeedRow()).isTrue();
        assertThat(dragostea.addedByUsername()).isNull();
        assertThat(dragostea.artists()).containsExactly("O-Zone");
        assertThat(dragostea.rowKey()).startsWith("song:");

        AdminCatalogRow ziMi = rowFor(rows, ZI_MI);
        assertThat(ziMi.status()).isEqualTo(CatalogSongStatus.ERROR);
        assertThat(ziMi.hasUnfinishedRecheck()).isTrue();
        assertThat(ziMi.addedByUsername()).isEqualTo("mara.k");

        assertThat(rowFor(rows, BARBIE_GIRL).status()).isEqualTo(CatalogSongStatus.PROCESSING);

        AdminCatalogRow takeFive = rowFor(rows, TAKE_FIVE);
        assertThat(takeFive.status()).isEqualTo(CatalogSongStatus.VERIFIED);
        assertThat(takeFive.hasFinishedRecheck()).isTrue();
        assertThat(takeFive.hasUnfinishedRecheck()).isFalse();

        AdminCatalogRow saNuMi = rowFor(rows, SA_NU_MI);
        assertThat(saNuMi.status()).isEqualTo(CatalogSongStatus.IN_QUEUE);
        assertThat(saNuMi.hasUnfinishedRecheck()).isTrue();

        AdminCatalogRow unknownOrigin = rowFor(rows, UNKNOWN_ORIGIN);
        assertThat(unknownOrigin.status()).isEqualTo(CatalogSongStatus.NEEDS_REVIEW);
        assertThat(unknownOrigin.latestQueueOrigin()).isNull();
        assertThat(unknownOrigin.hasPipelineRun()).isFalse();
        assertThat(unknownOrigin.hasAdminSeedRow()).isFalse();
        assertThat(unknownOrigin.releaseYear()).isNull();
        assertThat(unknownOrigin.createdAt()).isNull();
    }

    @Test
    void backlogItemsWithoutASongAreOneRowPerYoutubeIdPreferringTheActiveRow() {
        List<AdminCatalogRow> rows = repository.findPage(null, null, false, WHOLE_CATALOG, FIRST_OFFSET);

        AdminCatalogRow queuedOnly = rowFor(rows, QUEUED_ONLY_ID);
        assertThat(queuedOnly.status()).isEqualTo(CatalogSongStatus.IN_QUEUE);
        assertThat(queuedOnly.songId()).isNull();
        assertThat(queuedOnly.title()).isNull();
        assertThat(queuedOnly.artists()).isEmpty();
        assertThat(queuedOnly.rowKey()).startsWith("import:");
        assertThat(queuedOnly.latestQueueOrigin()).isEqualTo(PendingImportOrigin.ADMIN_SEED);
        assertThat(queuedOnly.hasPipelineRun()).isTrue();
        assertThat(queuedOnly.createdAt()).isEqualTo(Instant.parse("2026-10-16T08:00:00Z"));

        assertThat(rows).filteredOn(row -> FAILED_ONLY_ID.equals(row.youtubeId())).hasSize(1);
        assertThat(rowFor(rows, FAILED_ONLY_ID).status()).isEqualTo(CatalogSongStatus.ERROR);

        AdminCatalogRow requeued = rowFor(rows, REQUEUED_ID);
        assertThat(requeued.status()).isEqualTo(CatalogSongStatus.IN_QUEUE);
        assertThat(requeued.createdAt()).isEqualTo(Instant.parse("2026-10-09T07:00:00Z"));

        assertThat(titlesOrIds(rows)).doesNotContain("orphan-recheck-id", "done-without-song-id");
    }

    @Test
    void countsCoverEveryStatus() {
        Map<CatalogSongStatus, Long> counts = repository.countByStatus(null);

        assertThat(counts).containsEntry(CatalogSongStatus.VERIFIED, 2L)
                .containsEntry(CatalogSongStatus.NEEDS_REVIEW, 1L)
                .containsEntry(CatalogSongStatus.IN_QUEUE, 3L)
                .containsEntry(CatalogSongStatus.PROCESSING, 1L)
                .containsEntry(CatalogSongStatus.ERROR, 2L);
        assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(CATALOG_ROW_COUNT);
    }

    @Test
    void queueCountsCoverPendingAndProcessingRowsOfEveryOrigin() {
        AdminSongCatalogRepository.QueueCounts queueCounts = repository.countQueue();

        assertThat(queueCounts.waiting()).isEqualTo(WAITING_QUEUE_ROWS);
        assertThat(queueCounts.inProgress()).isEqualTo(IN_PROGRESS_QUEUE_ROWS);
    }

    @Test
    void queueCountsAreZeroWhenTheQueueIsEmpty() {
        jdbcTemplate.execute("TRUNCATE pipeline_runs, song_embedding_queue, pending_imports RESTART IDENTITY CASCADE");

        AdminSongCatalogRepository.QueueCounts queueCounts = repository.countQueue();

        assertThat(queueCounts.waiting()).isZero();
        assertThat(queueCounts.inProgress()).isZero();
    }

    @Test
    void retainedPendingImportsDoNotChangeCatalogRowsOrQueueCounts() {
        List<AdminCatalogRow> originalRows = repository.findPage(null, null, false, WHOLE_CATALOG, FIRST_OFFSET);
        AdminSongCatalogRepository.QueueCounts originalCounts = repository.countQueue();
        jdbcTemplate.update("""
                INSERT INTO pending_imports (youtube_id, origin, status, enqueued_at)
                VALUES (?, 'ADMIN_SEED', 'PROCESSING', now())
                """, "historical-only-id");

        assertThat(repository.findPage(null, null, false, WHOLE_CATALOG, FIRST_OFFSET)).isEqualTo(originalRows);
        assertThat(repository.countQueue()).isEqualTo(originalCounts);
    }

    @Test
    void theStatusFilterNarrowsTheRows() {
        List<AdminCatalogRow> inQueue = repository.findPage(null, CatalogSongStatus.IN_QUEUE, false, WHOLE_CATALOG, FIRST_OFFSET);

        assertThat(titlesOrIds(inQueue)).containsExactly(QUEUED_ONLY_ID, SA_NU_MI, REQUEUED_ID);
    }

    @Test
    void pagesFollowTheSameOrder() {
        List<AdminCatalogRow> firstPage = repository.findPage(null, null, false, SMALL_PAGE, FIRST_OFFSET);
        List<AdminCatalogRow> thirdPage = repository.findPage(null, null, false, SMALL_PAGE, THIRD_PAGE_OFFSET);

        assertThat(titlesOrIds(firstPage)).containsExactly(BARBIE_GIRL, QUEUED_ONLY_ID, FAILED_ONLY_ID, ZI_MI);
        assertThat(titlesOrIds(thirdPage)).containsExactly(UNKNOWN_ORIGIN);
    }

    @Test
    void searchMatchesTitleCaseInsensitivelyAndCountsFollowTheSearch() {
        List<AdminCatalogRow> rows = repository.findPage("ZI-M", null, false, WHOLE_CATALOG, FIRST_OFFSET);

        assertThat(titlesOrIds(rows)).containsExactly(ZI_MI);
        Map<CatalogSongStatus, Long> counts = repository.countByStatus("ZI-M");
        assertThat(counts).containsEntry(CatalogSongStatus.ERROR, 1L)
                .containsEntry(CatalogSongStatus.VERIFIED, 0L);
    }

    @Test
    void searchMatchesArtistNameBySubstring() {
        assertThat(titlesOrIds(repository.findPage("brubeck", null, false, WHOLE_CATALOG, FIRST_OFFSET)))
                .containsExactly(TAKE_FIVE);
    }

    @Test
    void searchMatchesAnExactYoutubeIdForSongsAndBacklogItems() {
        assertThat(titlesOrIds(repository.findPage(ZI_MI_YOUTUBE_ID, null, false, WHOLE_CATALOG, FIRST_OFFSET)))
                .containsExactly(ZI_MI);
        assertThat(titlesOrIds(repository.findPage(QUEUED_ONLY_ID, null, false, WHOLE_CATALOG, FIRST_OFFSET)))
                .containsExactly(QUEUED_ONLY_ID);
        assertThat(repository.findPage("queued-only", null, false, WHOLE_CATALOG, FIRST_OFFSET)).isEmpty();
    }

    @Test
    void likeWildcardsInSearchTextMatchLiterally() {
        insertSong("100% Pure Love", 1994, "pure-love-id", "VERIFIED", null, "2026-10-01T00:00:00Z");
        insertSong("1000 Pure Love", 1994, "pure-love-copy-id", "VERIFIED", null, "2026-10-01T00:00:00Z");
        insertSong("Track_One", 2001, "track-underscore-id", "VERIFIED", null, "2026-10-01T00:00:00Z");
        insertSong("TrackXOne", 2001, "track-letter-id", "VERIFIED", null, "2026-10-01T00:00:00Z");

        assertThat(titlesOrIds(repository.findPage("100%", null, false, WHOLE_CATALOG, FIRST_OFFSET)))
                .containsExactly("100% Pure Love");
        assertThat(titlesOrIds(repository.findPage("k_O", null, false, WHOLE_CATALOG, FIRST_OFFSET)))
                .containsExactly("Track_One");
    }

    @Test
    void filteringByProposedYearReturnsOnlySongsWithAProposal() {
        jdbcTemplate.update("UPDATE songs SET proposed_release_year = 2014 WHERE title = ?", ZI_MI);
        List<AdminCatalogRow> rows = repository.findPage(null, null, true, WHOLE_CATALOG, FIRST_OFFSET);
        assertThat(titlesOrIds(rows)).containsExactly(ZI_MI);
        assertThat(rows.getFirst().proposedReleaseYear()).isEqualTo(2014);
        assertThat(repository.countProposed(null)).isEqualTo(1);
    }

    @Test
    void recheckCandidatesListSongsByLastPipelineFinishFallingBackToWhenTheyWereAdded() {
        jdbcTemplate.update("UPDATE pipeline_runs SET finished_at = ? WHERE youtube_id = ?",
                OffsetDateTime.parse("2026-11-02T10:00:00Z"), "dragostea-id");
        jdbcTemplate.update("""
                INSERT INTO groups (invite_code, join_code, status, dj_mode, win_condition_card_count, created_at)
                VALUES ('invite', 'ABCD', 'IN_GAME', 'ROTATING', 10, now())
                """);
        Long groupId = jdbcTemplate.queryForObject("SELECT id FROM groups", Long.class);
        jdbcTemplate.update("""
                INSERT INTO game_sessions (group_id, status, dj_mode, win_condition_card_count, current_round_number, created_at)
                VALUES (?, 'ACTIVE', 'ROTATING', 10, 1, now())
                """, groupId);
        Long sessionId = jdbcTemplate.queryForObject("SELECT id FROM game_sessions", Long.class);
        jdbcTemplate.update("""
                INSERT INTO game_session_song_queue (session_id, queue_position, song_id)
                SELECT ?, 0, id FROM songs WHERE title = ?
                """, sessionId, BARBIE_GIRL);

        List<RecheckCandidateDTO> candidates = repository.findRecheckCandidates();

        assertThat(candidates).extracting(RecheckCandidateDTO::title).containsExactly(DRAGOSTEA, BARBIE_GIRL, ZI_MI,
                SA_NU_MI, TAKE_FIVE, UNKNOWN_ORIGIN);
        RecheckCandidateDTO dragostea = candidates.getFirst();
        assertThat(dragostea.lastCheckedAt()).isEqualTo(Instant.parse("2026-11-02T10:00:00Z"));
        assertThat(dragostea.artists()).containsExactly("O-Zone");
        assertThat(dragostea.releaseYear()).isEqualTo(2003);
        assertThat(dragostea.inActiveGame()).isFalse();
        RecheckCandidateDTO barbie = candidates.get(1);
        assertThat(barbie.lastCheckedAt()).isEqualTo(Instant.parse("2026-10-16T10:00:00Z"));
        assertThat(barbie.inActiveGame()).isTrue();
        RecheckCandidateDTO unknownOrigin = candidates.getLast();
        assertThat(unknownOrigin.releaseYear()).isNull();
        assertThat(unknownOrigin.lastCheckedAt()).isNull();
    }

    @Test
    void theTrigramIndexesExistAndServeTheSearch() {
        List<String> indexNames = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE indexdef ILIKE '%gin_trgm_ops%'", String.class);
        assertThat(indexNames).contains("songs_title_trgm_idx", "song_artists_name_trgm_idx");

        SingleConnectionDataSource singleConnection = new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true);
        try {
            JdbcTemplate explainTemplate = new JdbcTemplate(singleConnection);
            explainTemplate.execute("SET enable_seqscan = off");
            String plan = String.join("\n", explainTemplate.queryForList(
                    "EXPLAIN " + AdminSongCatalogRepository.pageSql("brubeck", null), String.class,
                    AdminSongCatalogRepository.pageParameters("brubeck", null, WHOLE_CATALOG, FIRST_OFFSET)));

            assertThat(plan).contains("songs_title_trgm_idx").contains("song_artists_name_trgm_idx");
        } finally {
            singleConnection.destroy();
        }
    }
}
