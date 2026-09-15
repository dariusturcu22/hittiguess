package org.dariusturcu.backend.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies V12 creates the alternate_youtube_ids and pending_imports tables and that a
 * given alternate YouTube ID can be linked to a canonical song only once.
 */
@Testcontainers
class CatalogSeedingSchemaMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    private static Connection connection;
    private static long songId;

    @BeforeAll
    static void migrateAndInsertOneSong() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO songs (title, release_year, youtube_id, verification_status) "
                            + "VALUES ('Canonical Song', 2001, 'canonical-yt-id', 'UNVERIFIED')");
            try (var resultSet = statement.executeQuery("SELECT id FROM songs WHERE youtube_id = 'canonical-yt-id'")) {
                resultSet.next();
                songId = resultSet.getLong("id");
            }
            statement.execute(
                    "INSERT INTO alternate_youtube_ids (youtube_id, song_id) VALUES ('alt-yt-id', " + songId + ")");
        }
    }

    @Test
    void theSameAlternateYoutubeIdCannotBeLinkedTwice() {
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "INSERT INTO alternate_youtube_ids (youtube_id, song_id) VALUES ('alt-yt-id', " + songId + ")");
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void aPendingImportRowPersistsWithItsStatusAndEnqueuedTimestamp() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO pending_imports (youtube_id, status, enqueued_at) "
                            + "VALUES ('pending-yt-id', 'PENDING', now())");
            try (var resultSet = statement.executeQuery(
                    "SELECT count(*) AS pending_count FROM pending_imports WHERE status = 'PENDING'")) {
                resultSet.next();
                assertThat(resultSet.getInt("pending_count")).isEqualTo(1);
            }
        }
    }
}
