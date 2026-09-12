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
 * Verifies V7 enables pgvector and adds a nullable vector(1536) embedding
 * column to songs, story 16's storage for verified-song duplicate detection.
 * Requires a Postgres image that bundles the pgvector extension binary
 * (pgvector/pgvector:pg18), a plain postgres image has no extension to load.
 */
@Testcontainers
class SongEmbeddingColumnMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    private static final int SONG_EMBEDDING_DIMENSIONS = 1536;

    private static Connection connection;

    private static String unitVectorLiteral() {
        String repeatedComponents = "1,".repeat(SONG_EMBEDDING_DIMENSIONS - 1);
        return "'[" + repeatedComponents + "1]'::vector";
    }

    @BeforeAll
    static void migrateAndConnect() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Test
    void enablesTheVectorExtension() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT count(*) AS extension_count FROM pg_extension WHERE extname = 'vector'")) {
            resultSet.next();
            assertThat(resultSet.getInt("extension_count")).isOne();
        }
    }

    @Test
    void addsANullableVectorEmbeddingColumnToSongs() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT udt_name, is_nullable FROM information_schema.columns "
                             + "WHERE table_name = 'songs' AND column_name = 'embedding'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("udt_name")).isEqualTo("vector");
            assertThat(resultSet.getString("is_nullable")).isEqualTo("YES");
        }
    }

    @Test
    void addsAnHnswCosineIndexOnEmbedding() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT indexdef FROM pg_indexes WHERE indexname = 'songs_embedding_hnsw_idx'")) {
            assertThat(resultSet.next()).isTrue();
            String indexDefinition = resultSet.getString("indexdef");
            assertThat(indexDefinition).containsIgnoringCase("USING hnsw");
            assertThat(indexDefinition).contains("vector_cosine_ops");
        }
    }

    @Test
    void supportsInsertingAndComparingEmbeddingsByCosineDistance() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username) VALUES ('embedding-test-user')");
            statement.execute("INSERT INTO playlists (invite_code) VALUES ('EMBED001')");
            statement.execute(
                    "INSERT INTO songs (title, release_year, youtube_id, added_by, embedding) "
                            + "VALUES ('Embedding Test Song', 2000, 'dQw4w9WgXcQ', "
                            + "(SELECT id FROM users WHERE username = 'embedding-test-user'), "
                            + unitVectorLiteral() + ")"
            );
            statement.execute(
                    "INSERT INTO song_playlists (song_id, playlist_id) "
                            + "SELECT (SELECT id FROM songs WHERE title = 'Embedding Test Song'), "
                            + "(SELECT id FROM playlists WHERE invite_code = 'EMBED001')"
            );

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT embedding <=> " + unitVectorLiteral() + " AS cosine_distance "
                            + "FROM songs WHERE title = 'Embedding Test Song'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getDouble("cosine_distance")).isEqualTo(0.0);
            }
        }
    }
}
