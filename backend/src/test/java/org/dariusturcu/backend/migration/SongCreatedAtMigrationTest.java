package org.dariusturcu.backend.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies V43 on top of the full history with songs already present, and checks the
 * created_at backfill from the oldest backlog row, the default for new rows, and pg_trgm.
 */
@Testcontainers
class SongCreatedAtMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    private static final String VERSION_BEFORE_CREATED_AT = "42";
    private static final String SEEDED_YOUTUBE_ID = "seeded-yt-id";
    private static final String UNQUEUED_YOUTUBE_ID = "unqueued-yt-id";
    private static final String NEW_YOUTUBE_ID = "new-yt-id";
    private static final Instant OLDEST_ENQUEUE = Instant.parse("2026-10-10T06:30:00Z");

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateAroundExistingSongs() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(VERSION_BEFORE_CREATED_AT)
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));

        jdbcTemplate.update("INSERT INTO songs (title, release_year, youtube_id) VALUES ('Seeded', 2001, ?)",
                SEEDED_YOUTUBE_ID);
        jdbcTemplate.update("INSERT INTO songs (title, release_year, youtube_id) VALUES ('Unqueued', 2002, ?)",
                UNQUEUED_YOUTUBE_ID);
        jdbcTemplate.update("""
                INSERT INTO pending_imports (youtube_id, status, enqueued_at) VALUES
                    (?, 'DONE', TIMESTAMP '2026-10-12 08:00:00'),
                    (?, 'FAILED', TIMESTAMP '2026-10-10 06:30:00')
                """, SEEDED_YOUTUBE_ID, SEEDED_YOUTUBE_ID);

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    private static OffsetDateTime createdAtOf(String youtubeId) {
        return jdbcTemplate.queryForObject("SELECT created_at FROM songs WHERE youtube_id = ?",
                OffsetDateTime.class, youtubeId);
    }

    @Test
    void anExistingSongTakesItsOldestBacklogRowAsItsCreationTime() {
        assertThat(createdAtOf(SEEDED_YOUTUBE_ID).toInstant()).isEqualTo(OLDEST_ENQUEUE);
    }

    @Test
    void anExistingSongWithoutBacklogRowsHasAnUnknownCreationTime() {
        assertThat(createdAtOf(UNQUEUED_YOUTUBE_ID)).isNull();
    }

    @Test
    void aRawInsertAfterTheMigrationIsStampedWithTheCurrentTime() {
        Instant beforeInsert = jdbcTemplate.queryForObject("SELECT now()", OffsetDateTime.class).toInstant();
        jdbcTemplate.update("INSERT INTO songs (title, release_year, youtube_id) VALUES ('New', 2003, ?)",
                NEW_YOUTUBE_ID);

        assertThat(createdAtOf(NEW_YOUTUBE_ID).toInstant()).isAfterOrEqualTo(beforeInsert);
    }

    @Test
    void theTrigramExtensionIsInstalled() {
        Integer extensionCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class);
        assertThat(extensionCount).isOne();
    }
}
