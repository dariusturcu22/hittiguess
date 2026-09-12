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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies V5 carries forward data from V1's original single-playlist-per-song
 * shape: each song row's playlist_id becomes one row in the new song_playlists
 * join table, and the column itself is gone afterward. Runs Flyway in two
 * steps, the pre-V5 schema first, then legacy-shaped rows inserted by hand,
 * then V5 itself, the same sequence a real pre-story-15 database goes through.
 */
@Testcontainers
class SongPlaylistJoinTableMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static Connection connection;
    private static long firstSongId;
    private static long secondSongId;
    private static long firstPlaylistId;
    private static long secondPlaylistId;

    @BeforeAll
    static void migrateToPreJoinTableSchemaInsertLegacyRowsThenMigrateTheRest() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("4")
                .load()
                .migrate();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username) VALUES ('legacy-user')");
            statement.execute("INSERT INTO playlists (invite_code, color) VALUES ('LEGACY1', 'FF0000')");
            statement.execute("INSERT INTO playlists (invite_code, color) VALUES ('LEGACY2', '00FF00')");
            statement.execute(
                    "INSERT INTO songs (title, release_year, youtube_id, playlist_id, added_by) "
                            + "VALUES ('First Legacy Song', 1999, 'dQw4w9WgXcQ', "
                            + "(SELECT id FROM playlists WHERE invite_code = 'LEGACY1'), "
                            + "(SELECT id FROM users WHERE username = 'legacy-user'))"
            );
            statement.execute(
                    "INSERT INTO songs (title, release_year, youtube_id, playlist_id, added_by) "
                            + "VALUES ('Second Legacy Song', 2001, 'abcdefghijk', "
                            + "(SELECT id FROM playlists WHERE invite_code = 'LEGACY2'), "
                            + "(SELECT id FROM users WHERE username = 'legacy-user'))"
            );

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT id, title FROM songs ORDER BY id")) {
                resultSet.next();
                firstSongId = resultSet.getLong("id");
                resultSet.next();
                secondSongId = resultSet.getLong("id");
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT id, invite_code FROM playlists ORDER BY id")) {
                resultSet.next();
                firstPlaylistId = resultSet.getLong("id");
                resultSet.next();
                secondPlaylistId = resultSet.getLong("id");
            }
        }

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Test
    void migratesEachSongsOriginalPlaylistLinkIntoTheJoinTable() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT playlist_id FROM song_playlists WHERE song_id = " + firstSongId)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong("playlist_id")).isEqualTo(firstPlaylistId);
            assertThat(resultSet.next()).isFalse();
        }
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT playlist_id FROM song_playlists WHERE song_id = " + secondSongId)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong("playlist_id")).isEqualTo(secondPlaylistId);
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void thePlaylistIdColumnIsGoneFromSongs() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT column_name FROM information_schema.columns WHERE table_name = 'songs'")) {
            List<String> columns = new ArrayList<>();
            while (resultSet.next()) {
                columns.add(resultSet.getString("column_name"));
            }
            assertThat(columns).doesNotContain("playlist_id");
        }
    }
}
