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
 * Verifies V2-V4 correctly carry forward data that predates them: song rows
 * created under V1's original single-artist, single-tag, no-verification-status
 * shape. Runs Flyway in two steps, baseline schema first, then legacy-shaped
 * rows inserted by hand, then the rest of the migrations, the same sequence a
 * real pre-story-23 database goes through.
 */
@Testcontainers
class SongSchemaMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    // The container above is static, one instance shared for the whole class, so this setup
    // runs once via @BeforeAll rather than per-test: re-running it per-test against the same
    // already-migrated database would try to insert the same rows twice.
    private static Connection connection;
    private static long taggedSongId;

    @BeforeAll
    static void migrateBaselineInsertLegacyRowsThenMigrateTheRest() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("1")
                .load()
                .migrate();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username) VALUES ('legacy-user')");
            statement.execute("INSERT INTO playlists (invite_code) VALUES ('LEGACY1')");
            statement.execute(
                    "INSERT INTO songs (artist, title, release_year, youtube_id, song_tag, playlist_id, added_by) "
                            + "VALUES ('Legacy Artist', 'Legacy Title', 1999, 'dQw4w9WgXcQ', 'SPECIAL', "
                            + "(SELECT id FROM playlists WHERE invite_code = 'LEGACY1'), "
                            + "(SELECT id FROM users WHERE username = 'legacy-user'))"
            );
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT id, title FROM songs ORDER BY id")) {
                resultSet.next();
                taggedSongId = resultSet.getLong("id");
            }
        }

        // Stops at V6, before V7's playlist and song data wipe, since this test checks V2-V4's
        // backfills, not anything V7 does.
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("6")
                .load()
                .migrate();
    }

    @Test
    void backfillsVerificationStatusForAPreExistingSong() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT verification_status FROM songs WHERE id = " + taggedSongId)) {
            resultSet.next();
            assertThat(resultSet.getString("verification_status")).isEqualTo("UNVERIFIED");
        }
    }

    @Test
    void migratesTheLegacyArtistStringIntoASingleMainSongArtist() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT name, role, display_order FROM song_artists WHERE song_id = " + taggedSongId)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("name")).isEqualTo("Legacy Artist");
            assertThat(resultSet.getString("role")).isEqualTo("MAIN");
            assertThat(resultSet.getInt("display_order")).isZero();
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void theOldArtistAndSongTagColumnsAreGone() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT column_name FROM information_schema.columns WHERE table_name = 'songs'")) {
            List<String> columns = new ArrayList<>();
            while (resultSet.next()) {
                columns.add(resultSet.getString("column_name"));
            }
            assertThat(columns).doesNotContain("artist", "song_tag");
        }
    }
}
