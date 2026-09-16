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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies V15 adds a not-null, default-false is_public column to playlists, and a
 * saved_playlists table with a unique constraint on (user_id, playlist_id).
 */
@Testcontainers
class PublicPlaylistsMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    private static Connection connection;

    @BeforeAll
    static void migrateAndConnect() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Test
    void addsANotNullIsPublicColumnDefaultingToFalse() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT is_nullable, column_default FROM information_schema.columns "
                             + "WHERE table_name = 'playlists' AND column_name = 'is_public'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("is_nullable")).isEqualTo("NO");
            assertThat(resultSet.getString("column_default")).contains("false");
        }
    }

    @Test
    void everyExistingPlaylistDefaultsToNotPublic() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username) VALUES ('public-playlist-migration-user')");
            statement.execute(
                    "INSERT INTO playlists (invite_code, owner_id) "
                            + "VALUES ('PUBMIG01', (SELECT id FROM users WHERE username = 'public-playlist-migration-user'))"
            );

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT is_public FROM playlists WHERE invite_code = 'PUBMIG01'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getBoolean("is_public")).isFalse();
            }
        }
    }

    @Test
    void savedPlaylistsTableExistsWithAUniqueConstraintOnUserAndPlaylist() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_name = 'saved_playlists'")) {
            assertThat(resultSet.next()).isTrue();
        }

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT constraint_name FROM information_schema.table_constraints "
                             + "WHERE table_name = 'saved_playlists' AND constraint_type = 'UNIQUE'")) {
            assertThat(resultSet.next()).isTrue();
        }
    }

    @Test
    void savingThePlaylistTwiceForTheSameUserViolatesTheUniqueConstraint() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username) VALUES ('saved-playlist-migration-user')");
            statement.execute(
                    "INSERT INTO playlists (invite_code, owner_id) "
                            + "VALUES ('PUBMIG02', (SELECT id FROM users WHERE username = 'saved-playlist-migration-user'))"
            );
            statement.execute(
                    "INSERT INTO saved_playlists (user_id, playlist_id, saved_at) "
                            + "VALUES ((SELECT id FROM users WHERE username = 'saved-playlist-migration-user'), "
                            + "(SELECT id FROM playlists WHERE invite_code = 'PUBMIG02'), now())"
            );

            assertThatDuplicateSaveIsRejected(statement);
        }
    }

    private void assertThatDuplicateSaveIsRejected(Statement statement) {
        assertThatThrownBy(() -> statement.execute(
                "INSERT INTO saved_playlists (user_id, playlist_id, saved_at) "
                        + "VALUES ((SELECT id FROM users WHERE username = 'saved-playlist-migration-user'), "
                        + "(SELECT id FROM playlists WHERE invite_code = 'PUBMIG02'), now())"
        )).isInstanceOf(SQLException.class);
    }
}
