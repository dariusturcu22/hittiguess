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
 * Verifies V5 carries forward a pre-story-46 database: user_playlists rows
 * with no owner or per-grant concept at all. Runs Flyway in two steps,
 * everything up to V4 first, then legacy rows inserted by hand, then V5, the
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
    private static long earlierUserId;
    private static long laterUserId;
    private static long sharedPlaylistId;
    private static long soloPlaylistId;

    @BeforeAll
    static void migrateUpToV4InsertLegacyRowsThenMigrateTheRest() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("4")
                .load()
                .migrate();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username, image_url) VALUES ('earlier-user', 'earlier.png')");
            statement.execute("INSERT INTO users (username, image_url) VALUES ('later-user', 'later.png')");
            try (ResultSet resultSet = statement.executeQuery("SELECT id, username FROM users ORDER BY id")) {
                resultSet.next();
                earlierUserId = resultSet.getLong("id");
                resultSet.next();
                laterUserId = resultSet.getLong("id");
            }

            statement.execute("INSERT INTO playlists (name, invite_code) VALUES ('Shared playlist', 'SHARED1')");
            statement.execute("INSERT INTO playlists (name, invite_code) VALUES ('Solo playlist', 'SOLO1')");
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT id, invite_code FROM playlists ORDER BY id")) {
                resultSet.next();
                sharedPlaylistId = resultSet.getLong("id");
                resultSet.next();
                soloPlaylistId = resultSet.getLong("id");
            }

            statement.execute("INSERT INTO user_playlists (user_id, playlist_id) VALUES (" + laterUserId + ", " + sharedPlaylistId + ")");
            statement.execute("INSERT INTO user_playlists (user_id, playlist_id) VALUES (" + earlierUserId + ", " + sharedPlaylistId + ")");
            statement.execute("INSERT INTO user_playlists (user_id, playlist_id) VALUES (" + earlierUserId + ", " + soloPlaylistId + ")");
        }

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Test
    void everyPlaylistGetsADeterminableOwner() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT id, owner_id FROM playlists WHERE owner_id IS NULL")) {
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void theOwnerOfASharedPlaylistIsItsEarliestCreatedMember() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT owner_id FROM playlists WHERE id = " + sharedPlaylistId)) {
            resultSet.next();
            assertThat(resultSet.getLong("owner_id")).isEqualTo(earlierUserId);
        }
    }

    @Test
    void everyExistingMembershipCarriesFullGrantsForward() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT can_read, can_write, can_delete FROM playlist_memberships "
                             + "WHERE playlist_id = " + sharedPlaylistId + " AND user_id = " + laterUserId)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getBoolean("can_read")).isTrue();
            assertThat(resultSet.getBoolean("can_write")).isTrue();
            assertThat(resultSet.getBoolean("can_delete")).isTrue();
        }
    }

    @Test
    void migratedMembershipsDefaultTheirIdentityToTheAccountsOwnUsernameAndAvatar() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT display_name, avatar_url FROM playlist_memberships "
                             + "WHERE playlist_id = " + soloPlaylistId + " AND user_id = " + earlierUserId)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("display_name")).isEqualTo("earlier-user");
            assertThat(resultSet.getString("avatar_url")).isEqualTo("earlier.png");
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
