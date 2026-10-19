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
 * Verifies V5 drops song_tags entirely, with no automatic backfill into the new
 * genre column: there's no reliable mapping from the old PLAYLIST/SPECIAL/ANIME
 * categories to a real genre, so a song that had a tag before V5 simply has a
 * null genre after it. Runs Flyway in two steps, the pre-V5 schema first with a
 * tagged legacy row inserted by hand, then V5 itself.
 */
@Testcontainers
class SongGenreMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    private static Connection connection;
    private static long taggedSongId;

    @BeforeAll
    static void migrateToPreGenreSchemaInsertATaggedRowThenMigrateTheRest() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("4")
                .load()
                .migrate();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username) VALUES ('legacy-user')");
            statement.execute("INSERT INTO playlists (invite_code) VALUES ('LEGACY1')");
            statement.execute(
                    "INSERT INTO songs (title, release_year, youtube_id, playlist_id, added_by) "
                            + "VALUES ('Legacy Title', 1999, 'dQw4w9WgXcQ', "
                            + "(SELECT id FROM playlists WHERE invite_code = 'LEGACY1'), "
                            + "(SELECT id FROM users WHERE username = 'legacy-user'))"
            );
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT id FROM songs WHERE title = 'Legacy Title'")) {
                resultSet.next();
                taggedSongId = resultSet.getLong("id");
            }
            statement.execute("INSERT INTO song_tags (song_id, tag) VALUES (" + taggedSongId + ", 'SPECIAL')");
        }

        // Stops at V6, the genre migration itself: V7 clears every song and playlist outright,
        // which would wipe the legacy row this test exists to check.
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("6")
                .load()
                .migrate();
    }

    @Test
    void theSongTagsTableIsGone() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_name = 'song_tags'")) {
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void aPreviouslyTaggedSongHasNoGenreAfterMigrating() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT genre FROM songs WHERE id = " + taggedSongId)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("genre")).isNull();
        }
    }

    @Test
    void theGenreColumnAcceptsAFreeTextValue() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("UPDATE songs SET genre = 'Alternative Rock' WHERE id = " + taggedSongId);
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT genre FROM songs WHERE id = " + taggedSongId)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("genre")).isEqualTo("Alternative Rock");
            }
        }
    }

    @Test
    void theSongsTableStillHasNoTagColumn() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT column_name FROM information_schema.columns WHERE table_name = 'songs'")) {
            List<String> columns = new ArrayList<>();
            while (resultSet.next()) {
                columns.add(resultSet.getString("column_name"));
            }
            assertThat(columns).doesNotContain("song_tag").contains("genre");
        }
    }
}
