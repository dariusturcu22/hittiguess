package org.dariusturcu.backend.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies V7 clears every pre-story-46 playlist and song, since none of them
 * carry a real owner, rather than backfilling one. Runs Flyway in two steps,
 * everything up to V6 first, then legacy rows inserted by hand, then V7, the
 * same sequence a real pre-story-46 database goes through.
 */
@Testcontainers
class PlaylistOwnershipMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    // The container above is static, one instance shared for the whole class, so this setup
    // runs once via @BeforeAll rather than per-test: re-running it per-test against the same
    // already-migrated database would try to insert the same rows twice.
    private static Connection connection;
    private static long survivingUserId;

    @BeforeAll
    static void migrateUpToV6InsertLegacyRowsThenMigrateTheRest() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("6")
                .load()
                .migrate();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username, image_url) VALUES ('surviving-user', 'surviving.png')");
            try (ResultSet resultSet = statement.executeQuery("SELECT id FROM users ORDER BY id")) {
                resultSet.next();
                survivingUserId = resultSet.getLong("id");
            }

            statement.execute("INSERT INTO playlists (name, invite_code) VALUES ('Legacy playlist', 'LEGACY1')");
            long legacyPlaylistId;
            try (ResultSet resultSet = statement.executeQuery("SELECT id FROM playlists")) {
                resultSet.next();
                legacyPlaylistId = resultSet.getLong("id");
            }
            statement.execute("INSERT INTO user_playlists (user_id, playlist_id) VALUES (" + survivingUserId + ", " + legacyPlaylistId + ")");
            statement.execute("INSERT INTO songs (title, release_year, added_by) VALUES ('Legacy song', 2000, " + survivingUserId + ")");

            long legacySongId;
            try (ResultSet resultSet = statement.executeQuery("SELECT id FROM songs")) {
                resultSet.next();
                legacySongId = resultSet.getLong("id");
            }
            statement.execute("INSERT INTO song_playlists (song_id, playlist_id) VALUES (" + legacySongId + ", " + legacyPlaylistId + ")");
        }

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Test
    void everyPreExistingPlaylistIsGone() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id FROM playlists")) {
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void everyPreExistingSongIsGone() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id FROM songs")) {
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void everyPreExistingSongPlaylistLinkIsGone() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT song_id FROM song_playlists")) {
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void usersSurviveTheMigrationUntouched() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT username FROM users WHERE id = " + survivingUserId)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("username")).isEqualTo("surviving-user");
        }
    }

    @Test
    void theOwnerIdColumnIsNotNull() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT is_nullable FROM information_schema.columns "
                             + "WHERE table_name = 'playlists' AND column_name = 'owner_id'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("is_nullable")).isEqualTo("NO");
        }
    }

    @Test
    void theOldUserPlaylistsTableIsGone() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_name = 'user_playlists'")) {
            assertThat(resultSet.next()).isFalse();
        }
    }
}
